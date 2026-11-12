package com.tickets.adapter.out.cache;

import com.tickets.application.port.out.PromptTemplateRepository;
import com.tickets.domain.PromptTemplate;

import java.time.Duration;
import java.util.Optional;

/** El prompt activo se lee en cada analisis y casi nunca cambia; cualquier cambio lo expulsa. */
public class PromptRepositorioConCache implements PromptTemplateRepository {

    private final PromptTemplateRepository delegado;
    private final CacheAside cache;
    private final Duration ttl;

    public PromptRepositorioConCache(PromptTemplateRepository delegado, CacheAside cache, Duration ttl) {
        this.delegado = delegado;
        this.cache = cache;
        this.ttl = ttl;
    }

    @Override
    public Optional<PromptTemplate> buscarActivo(String nombre) {
        return cache.obtener("prompt:activo:" + nombre, ttl, PromptTemplate.class, () -> delegado.buscarActivo(nombre));
    }

    @Override
    public Optional<PromptTemplate> buscar(String nombre, int version) {
        return delegado.buscar(nombre, version);
    }

    @Override
    public PromptTemplate guardar(PromptTemplate promptTemplate) {
        PromptTemplate guardado = delegado.guardar(promptTemplate);
        cache.eliminar("prompt:activo:" + guardado.nombre());
        return guardado;
    }

    @Override
    public void activarExclusivamente(String nombre, int version) {
        delegado.activarExclusivamente(nombre, version);
        cache.eliminar("prompt:activo:" + nombre);
    }
}
