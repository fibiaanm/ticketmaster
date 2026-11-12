package com.tickets.adapter.out.analysis;

import com.tickets.application.port.out.ColaTareas;
import com.tickets.application.port.out.DisparadorAnalisis;
import com.tickets.application.port.out.TipoTarea;
import com.tickets.domain.Ticket;
import com.tickets.shared.log.Contexto;

/** ANALYSIS_MODE=async: encola la tarea (atomica con el alta del ticket); la procesa el worker. */
public class AsyncDisparadorAnalisis implements DisparadorAnalisis {

    private final ColaTareas cola;

    public AsyncDisparadorAnalisis(ColaTareas cola) {
        this.cola = cola;
    }

    @Override
    public void registrar(Ticket ticket) {
        cola.encolar(TipoTarea.ANALIZAR, ticket.getId(), Contexto.requestId());
    }

    @Override
    public void ejecutarTrasCommit(Ticket ticket) {
        // el worker se encarga
    }
}
