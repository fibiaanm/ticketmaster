package com.tickets.domain.exception;

/** Regla de negocio o dato invalido -> HTTP 400. */
public class SolicitudInvalidaException extends RuntimeException {
    public SolicitudInvalidaException(String mensaje) {
        super(mensaje);
    }
}
