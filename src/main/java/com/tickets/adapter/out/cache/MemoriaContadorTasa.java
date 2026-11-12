package com.tickets.adapter.out.cache;

import com.tickets.application.port.out.ContadorTasa;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** Contador en memoria: respaldo cuando Valkey no responde, y pruebas. */
public class MemoriaContadorTasa implements ContadorTasa {

    private record Ventana(AtomicLong cuenta, Instant expira) {
    }

    private final Map<String, Ventana> ventanas = new ConcurrentHashMap<>();
    private final Clock reloj;

    public MemoriaContadorTasa(Clock reloj) {
        this.reloj = reloj;
    }

    @Override
    public long incrementar(String clave, Duration ventana) {
        Instant ahora = reloj.instant();
        if (ventanas.size() > 10_000) {   // evita crecimiento sin limite
            ventanas.values().removeIf(v -> !v.expira().isAfter(ahora));
        }
        Ventana v = ventanas.compute(clave, (k, actual) ->
                actual == null || !actual.expira().isAfter(ahora)
                        ? new Ventana(new AtomicLong(), ahora.plus(ventana)) : actual);
        return v.cuenta().incrementAndGet();
    }
}
