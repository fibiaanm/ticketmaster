package com.tickets.application.port.out;

/** Tarea encolada. intentos incluye el intento en curso. */
public record Tarea(Long id, TipoTarea tipo, Long ticketId, int intentos, String requestId) {
}
