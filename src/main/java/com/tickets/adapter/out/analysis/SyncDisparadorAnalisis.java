package com.tickets.adapter.out.analysis;

import com.tickets.application.port.in.AnalizarTicketUseCase;
import com.tickets.application.port.out.DisparadorAnalisis;
import com.tickets.domain.Ticket;

/** ANALYSIS_MODE=sync: el analisis se ejecuta dentro de la peticion, despues de confirmar el ticket. */
public class SyncDisparadorAnalisis implements DisparadorAnalisis {

    private final AnalizarTicketUseCase analizar;

    public SyncDisparadorAnalisis(AnalizarTicketUseCase analizar) {
        this.analizar = analizar;
    }

    @Override
    public void registrar(Ticket ticket) {
        // nada que registrar: se analiza tras el commit
    }

    @Override
    public void ejecutarTrasCommit(Ticket ticket) {
        analizar.analizar(ticket.getId());
    }
}
