package com.tickets.domain.exception;

public class TecnicoInvalidoException extends SolicitudInvalidaException {
    public TecnicoInvalidoException(Long usuarioId) {
        super("El usuario con id %d no tiene rol SOPORTE y no puede ser asignado como técnico".formatted(usuarioId));
    }
}
