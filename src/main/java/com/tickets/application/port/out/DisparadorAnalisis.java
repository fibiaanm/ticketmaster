package com.tickets.application.port.out;

import com.tickets.domain.Ticket;

/** Estrategia de ejecucion del analisis: sincrona (en la peticion) o asincrona (cola + worker). */
public interface DisparadorAnalisis {
    /** Dentro de la transaccion de alta: el modo asincrono encola aqui, de forma atomica con el ticket. */
    void registrar(Ticket ticket);

    /** Despues de confirmar la transaccion: el modo sincrono analiza aqui. */
    void ejecutarTrasCommit(Ticket ticket);
}
