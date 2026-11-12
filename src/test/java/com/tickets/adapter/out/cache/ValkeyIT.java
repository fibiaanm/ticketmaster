package com.tickets.adapter.out.cache;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Prueba contra un Valkey real (el del compose). Se omite si no esta disponible. */
class ValkeyIT {

    StringRedisTemplate redis;

    static StringRedisTemplate plantilla(String host, int puerto) {
        var config = new RedisStandaloneConfiguration(host, puerto);
        var fabrica = new LettuceConnectionFactory(config);
        fabrica.afterPropertiesSet();
        var t = new StringRedisTemplate(fabrica);
        t.afterPropertiesSet();
        return t;
    }

    @BeforeEach
    void conectar() {
        redis = plantilla(System.getenv().getOrDefault("VALKEY_HOST", "localhost"), 6379);
        boolean disponible;
        try {
            disponible = "PONG".equalsIgnoreCase(redis.getConnectionFactory().getConnection().ping());
        } catch (RuntimeException e) {
            disponible = false;
        }
        Assumptions.assumeTrue(disponible, "Valkey no disponible: prueba omitida");
    }

    @Test
    void laCacheGuardaLeeEliminaYExpira() throws Exception {
        var cache = new ValkeyAlmacenCache(redis);
        String clave = "it:" + UUID.randomUUID();

        cache.guardar(clave, "valor", Duration.ofSeconds(1));
        assertThat(cache.leer(clave)).contains("valor");
        Thread.sleep(1300);
        assertThat(cache.leer(clave)).isEmpty();

        cache.guardar(clave, "otro", Duration.ofSeconds(30));
        cache.eliminar(clave);
        assertThat(cache.leer(clave)).isEmpty();
    }

    @Test
    void elContadorCuentaEnValkeyYExpiraLaVentana() throws Exception {
        var contador = new ValkeyContadorTasa(redis, new MemoriaContadorTasa(Clock.systemUTC()));
        String clave = "it:" + UUID.randomUUID();

        assertThat(contador.incrementar(clave, Duration.ofSeconds(1))).isEqualTo(1);
        assertThat(contador.incrementar(clave, Duration.ofSeconds(1))).isEqualTo(2);
        Thread.sleep(2300);
        assertThat(contador.incrementar(clave, Duration.ofSeconds(1))).isEqualTo(1);
    }

    @Test
    void siValkeyNoResponderLaCacheDegradaYElContadorUsaRespaldoLocal() {
        var caido = plantilla("127.0.0.1", 1);   // puerto sin servicio
        var cache = new ValkeyAlmacenCache(caido);
        assertThat(cache.leer("x")).isEmpty();
        cache.guardar("x", "y", Duration.ofSeconds(5));   // no lanza
        cache.eliminar("x");                              // no lanza

        var contador = new ValkeyContadorTasa(caido, new MemoriaContadorTasa(Clock.systemUTC()));
        assertThat(contador.incrementar("k", Duration.ofSeconds(60))).isEqualTo(1);
        assertThat(contador.incrementar("k", Duration.ofSeconds(60))).isEqualTo(2);
    }
}
