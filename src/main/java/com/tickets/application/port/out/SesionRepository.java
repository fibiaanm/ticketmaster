package com.tickets.application.port.out;

import com.tickets.domain.Sesion;

import java.time.Instant;
import java.util.Optional;

public interface SesionRepository {
    void guardar(String tokenHash, Long usuarioId, Instant expiraEn);

    Optional<Sesion> buscarPorHash(String tokenHash);

    void revocar(String tokenHash);
}
