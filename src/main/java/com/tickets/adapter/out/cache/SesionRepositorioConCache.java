package com.tickets.adapter.out.cache;

import com.tickets.application.port.out.SesionRepository;
import com.tickets.domain.Sesion;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/** Cachea la sesion por hash de token; revocar la expulsa de inmediato. La vigencia se sigue evaluando al leer. */
public class SesionRepositorioConCache implements SesionRepository {

    private final SesionRepository delegado;
    private final CacheAside cache;
    private final Duration ttl;

    public SesionRepositorioConCache(SesionRepository delegado, CacheAside cache, Duration ttl) {
        this.delegado = delegado;
        this.cache = cache;
        this.ttl = ttl;
    }

    @Override
    public void guardar(String tokenHash, Long usuarioId, Instant expiraEn) {
        delegado.guardar(tokenHash, usuarioId, expiraEn);
    }

    @Override
    public Optional<Sesion> buscarPorHash(String tokenHash) {
        return cache.obtener("sesion:" + tokenHash, ttl, Sesion.class, () -> delegado.buscarPorHash(tokenHash));
    }

    @Override
    public void revocar(String tokenHash) {
        delegado.revocar(tokenHash);
        cache.eliminar("sesion:" + tokenHash);
    }
}
