package com.tickets.adapter.out.persistence;

import com.tickets.application.port.out.SesionRepository;
import com.tickets.domain.Sesion;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

@Component("sesionRepositoryJpa")
@Transactional
class JpaSesionRepository implements SesionRepository {

    private final SesionJpaRepository jpa;
    private final Clock reloj;

    JpaSesionRepository(SesionJpaRepository jpa, Clock reloj) {
        this.jpa = jpa;
        this.reloj = reloj;
    }

    @Override
    public void guardar(String tokenHash, Long usuarioId, Instant expiraEn) {
        SesionEntity e = new SesionEntity();
        e.tokenHash = tokenHash;
        e.usuarioId = usuarioId;
        e.expiraEn = expiraEn;
        e.revocada = false;
        e.fechaCreacion = reloj.instant();
        jpa.save(e);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Sesion> buscarPorHash(String tokenHash) {
        return jpa.findByTokenHash(tokenHash)
                .map(e -> new Sesion(e.id, e.tokenHash, e.usuarioId, e.expiraEn, e.revocada));
    }

    @Override
    public void revocar(String tokenHash) {
        jpa.revocar(tokenHash);
    }
}
