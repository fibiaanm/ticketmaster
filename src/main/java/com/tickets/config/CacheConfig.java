package com.tickets.config;

import com.tickets.adapter.out.cache.*;
import com.tickets.application.port.out.*;
import com.tickets.application.service.Limitador;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Clock;
import java.time.Duration;

/**
 * Cache y rate limit sobre Valkey (CACHE_STORE=valkey) o en memoria (CACHE_STORE=memory).
 * Los repositorios cacheados son decoradores @Primary de los adaptadores JPA.
 */
@Configuration
@EnableConfigurationProperties(RateLimitProperties.class)
public class CacheConfig {

    @Bean
    AlmacenCache almacenCache(Clock clock, ObjectProvider<StringRedisTemplate> redis,
                              @Value("${cache.store:valkey}") String store) {
        return "valkey".equalsIgnoreCase(store) ? new ValkeyAlmacenCache(redis.getObject()) : new MemoriaAlmacenCache(clock);
    }

    @Bean
    ContadorTasa contadorTasa(Clock clock, ObjectProvider<StringRedisTemplate> redis,
                              @Value("${cache.store:valkey}") String store) {
        ContadorTasa local = new MemoriaContadorTasa(clock);
        return "valkey".equalsIgnoreCase(store) ? new ValkeyContadorTasa(redis.getObject(), local) : local;
    }

    @Bean
    CacheAside cacheAside(AlmacenCache almacen) {
        return new CacheAside(almacen);
    }

    @Bean
    Limitador limitador(ContadorTasa contador, Clock clock) {
        return new Limitador(contador, clock);
    }

    @Bean
    @Primary
    UsuarioRepository usuarioRepository(@Qualifier("usuarioRepositoryJpa") UsuarioRepository jpa, CacheAside cache,
                                        @Value("${cache.ttl.usuario:60s}") Duration ttl) {
        return new UsuarioRepositorioConCache(jpa, cache, ttl);
    }

    @Bean
    @Primary
    SesionRepository sesionRepository(@Qualifier("sesionRepositoryJpa") SesionRepository jpa, CacheAside cache,
                                      @Value("${cache.ttl.sesion:30s}") Duration ttl) {
        return new SesionRepositorioConCache(jpa, cache, ttl);
    }

    @Bean
    @Primary
    PromptTemplateRepository promptTemplateRepository(@Qualifier("promptRepositoryJpa") PromptTemplateRepository jpa,
                                                      CacheAside cache, @Value("${cache.ttl.prompt:60s}") Duration ttl) {
        return new PromptRepositorioConCache(jpa, cache, ttl);
    }

    @Bean
    @Primary
    MetricasRepository metricasRepository(@Qualifier("metricasRepositoryJpa") MetricasRepository jpa,
                                          CacheAside cache, @Value("${cache.ttl.metricas:60s}") Duration ttl) {
        return new MetricasRepositorioConCache(jpa, cache, ttl);
    }
}
