package com.tickets.domain.exception;

/** Fallo del analisis de IA (conexion o respuesta invalida). Dispara el fallback. */
public abstract class ErrorIAException extends RuntimeException {
    protected ErrorIAException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
