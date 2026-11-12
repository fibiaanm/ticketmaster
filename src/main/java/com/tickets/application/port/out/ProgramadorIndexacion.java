package com.tickets.application.port.out;

/** Avisa que un ticket cambio y debe reindexarse en el buscador (outbox: se ejecuta dentro de la transaccion). */
public interface ProgramadorIndexacion {
    void programar(Long ticketId);
}
