package com.tickets.application.port.in;

import com.tickets.domain.Actor;
import com.tickets.domain.Rol;

import java.time.Instant;
import java.util.Optional;

public interface AutenticarUseCase {
    ResultadoLogin login(String correo, String password);

    /** Autenticacion por JWT (Authorization: Bearer). */
    Optional<Actor> autenticarJwt(String jwt);

    /** Autenticacion por token de sesion (X-Ticket-Auth). */
    Optional<Actor> autenticarSesion(String tokenSesion);

    void cerrarSesion(String tokenSesion);

    record ResultadoLogin(String accessToken, Instant accessTokenExpiraEn, String sessionToken,
                          Instant sessionExpiraEn, Long usuarioId, String nombre, Rol rol) {
    }
}
