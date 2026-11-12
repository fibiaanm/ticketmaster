package com.tickets.adapter.out.cache;

import com.tickets.application.port.out.ContadorTasa;
import com.tickets.shared.log.Log;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Contador compartido entre instancias sobre Valkey (INCR + EXPIRE). Si Valkey falla, usa un contador local:
 * el limite deja de ser global pero sigue protegiendo cada instancia (nunca "fail open" total).
 */
public class ValkeyContadorTasa implements ContadorTasa {

    private static final String PREFIJO = "tickets:rl:";
    private static final long SILENCIO_MS = 30_000;

    private final StringRedisTemplate redis;
    private final ContadorTasa respaldo;
    private final AtomicLong ultimoAviso = new AtomicLong();

    public ValkeyContadorTasa(StringRedisTemplate redis, ContadorTasa respaldo) {
        this.redis = redis;
        this.respaldo = respaldo;
    }

    @Override
    public long incrementar(String clave, Duration ventana) {
        try {
            Long total = redis.opsForValue().increment(PREFIJO + clave);
            if (total == null) {
                return respaldo.incrementar(clave, ventana);
            }
            if (total == 1L) {
                redis.expire(PREFIJO + clave, ventana.plusSeconds(1));
            }
            return total;
        } catch (RuntimeException e) {
            long ahora = System.currentTimeMillis();
            if (ahora - ultimoAviso.get() > SILENCIO_MS) {
                ultimoAviso.set(ahora);
                Log.warn("Valkey no disponible; rate limit local", Map.of("causa", e.getClass().getSimpleName()));
            }
            return respaldo.incrementar(clave, ventana);
        }
    }
}
