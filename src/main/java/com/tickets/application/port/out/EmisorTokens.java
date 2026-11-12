package com.tickets.application.port.out;

import com.tickets.domain.Actor;
import com.tickets.domain.Usuario;

import java.time.Instant;
import java.util.Optional;

/** Emision y validacion de JWT. */
public interface EmisorTokens {
    Token emitir(Usuario usuario);

    Optional<Actor> validar(String jwt);

    record Token(String valor, Instant expiraEn) {
    }
}
