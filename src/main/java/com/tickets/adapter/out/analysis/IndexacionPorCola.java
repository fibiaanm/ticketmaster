package com.tickets.adapter.out.analysis;

import com.tickets.application.port.out.ColaTareas;
import com.tickets.application.port.out.ProgramadorIndexacion;
import com.tickets.application.port.out.TipoTarea;
import com.tickets.shared.log.Contexto;

/** Outbox: encola la reindexacion en la misma transaccion que el cambio. */
public class IndexacionPorCola implements ProgramadorIndexacion {

    private final ColaTareas cola;

    public IndexacionPorCola(ColaTareas cola) {
        this.cola = cola;
    }

    @Override
    public void programar(Long ticketId) {
        cola.encolar(TipoTarea.INDEXAR, ticketId, Contexto.requestId());
    }
}
