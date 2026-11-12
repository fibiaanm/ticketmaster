package com.tickets.domain.exception;

public class RespuestaIAInvalidaException extends ErrorIAException {
    public RespuestaIAInvalidaException(String mensaje) {
        super(mensaje, null);
    }

    public RespuestaIAInvalidaException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
