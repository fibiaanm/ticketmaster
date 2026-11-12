package com.tickets.application.port.in;

public interface ProcesarColaUseCase {
    /** @return true si proceso una tarea, false si la cola estaba vacia. */
    boolean procesarSiguiente();
}
