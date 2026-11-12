package com.tickets.domain.exception;

/** Otro proceso modifico el mismo recurso mientras se actualizaba (bloqueo optimista) -> HTTP 409. */
public class ConflictoConcurrenciaException extends RuntimeException {
    public ConflictoConcurrenciaException(String mensaje) {
        super(mensaje);
    }
}
