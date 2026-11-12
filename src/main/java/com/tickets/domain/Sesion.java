package com.tickets.domain;

import java.time.Instant;

public record Sesion(Long id, String tokenHash, Long usuarioId, Instant expiraEn, boolean revocada) {
    public boolean estaVigente(Instant ahora) {
        return !revocada && expiraEn.isAfter(ahora);
    }
}
