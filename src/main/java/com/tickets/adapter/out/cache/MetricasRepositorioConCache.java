package com.tickets.adapter.out.cache;

import com.tickets.application.port.out.MetricasRepository;
import com.tickets.application.port.out.MetricasTickets;

import java.time.Duration;

/** Las metricas son una consulta agregada cara que tolera unos segundos de retraso: solo expira por TTL. */
public class MetricasRepositorioConCache implements MetricasRepository {

    private final MetricasRepository delegado;
    private final CacheAside cache;
    private final Duration ttl;

    public MetricasRepositorioConCache(MetricasRepository delegado, CacheAside cache, Duration ttl) {
        this.delegado = delegado;
        this.cache = cache;
        this.ttl = ttl;
    }

    @Override
    public MetricasTickets calcular() {
        return cache.obtenerSiempre("metricas:tickets", ttl, MetricasTickets.class, delegado::calcular);
    }
}
