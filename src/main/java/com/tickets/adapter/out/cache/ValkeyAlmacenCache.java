package com.tickets.adapter.out.cache;

import com.tickets.application.port.out.AlmacenCache;
import com.tickets.shared.log.Log;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/** Cache sobre Valkey. Si Valkey falla, se comporta como cache vacia (la operacion sigue contra la BD). */
public class ValkeyAlmacenCache implements AlmacenCache {

    private static final String PREFIJO = "tickets:";
    private static final long SILENCIO_MS = 30_000;

    private final StringRedisTemplate redis;
    private final AtomicLong ultimoAviso = new AtomicLong();

    public ValkeyAlmacenCache(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public Optional<String> leer(String clave) {
        try {
            return Optional.ofNullable(redis.opsForValue().get(PREFIJO + clave));
        } catch (RuntimeException e) {
            avisar(e);
            return Optional.empty();
        }
    }

    @Override
    public void guardar(String clave, String valor, Duration ttl) {
        try {
            redis.opsForValue().set(PREFIJO + clave, valor, ttl);
        } catch (RuntimeException e) {
            avisar(e);
        }
    }

    @Override
    public void eliminar(String clave) {
        try {
            redis.delete(PREFIJO + clave);
        } catch (RuntimeException e) {
            avisar(e);
        }
    }

    private void avisar(RuntimeException e) {
        long ahora = System.currentTimeMillis();
        if (ahora - ultimoAviso.get() > SILENCIO_MS) {
            ultimoAviso.set(ahora);
            Log.warn("Valkey no disponible; la cache se omite", Map.of("causa", e.getClass().getSimpleName()));
        }
    }
}
