package com.tickets.adapter.out.cache;

import com.tickets.application.port.out.AlmacenCache;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Cache en memoria del proceso (pruebas, o CACHE_STORE=memory sin Valkey). */
public class MemoriaAlmacenCache implements AlmacenCache {

    private record Entrada(String valor, Instant expira) {
    }

    private final Map<String, Entrada> datos = new ConcurrentHashMap<>();
    private final Clock reloj;

    public MemoriaAlmacenCache(Clock reloj) {
        this.reloj = reloj;
    }

    @Override
    public Optional<String> leer(String clave) {
        Entrada e = datos.get(clave);
        if (e == null) {
            return Optional.empty();
        }
        if (!e.expira().isAfter(reloj.instant())) {
            datos.remove(clave);
            return Optional.empty();
        }
        return Optional.of(e.valor());
    }

    @Override
    public void guardar(String clave, String valor, Duration ttl) {
        datos.put(clave, new Entrada(valor, reloj.instant().plus(ttl)));
    }

    @Override
    public void eliminar(String clave) {
        datos.remove(clave);
    }
}
