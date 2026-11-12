package com.tickets.domain.exception;

public class RecursoNoEncontradoException extends RuntimeException {
    public RecursoNoEncontradoException(String mensaje) {
        super(mensaje);
    }

    public static RecursoNoEncontradoException ticket(Long id) {
        return new RecursoNoEncontradoException("No se encontró el ticket con id " + id);
    }

    public static RecursoNoEncontradoException usuario(Long id) {
        return new RecursoNoEncontradoException("No se encontró el usuario con id " + id);
    }
}
