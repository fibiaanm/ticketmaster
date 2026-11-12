package com.tickets.domain.exception;

public class ConexionIAException extends ErrorIAException {
    private final boolean reintentable;

    public ConexionIAException(String mensaje, boolean reintentable, Throwable causa) {
        super(mensaje, causa);
        this.reintentable = reintentable;
    }

    public boolean esReintentable() {
        return reintentable;
    }
}
