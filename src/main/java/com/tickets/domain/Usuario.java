package com.tickets.domain;

import java.time.Instant;

public record Usuario(Long id, String nombre, String correo, String passwordHash, Rol rol, Instant fechaCreacion) {

    public Actor comoActor() {
        return new Actor(id, rol);
    }
}
