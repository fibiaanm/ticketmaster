package com.tickets.application.service;

import com.tickets.application.Pagina;
import com.tickets.application.port.in.ReindexarBusquedaUseCase;
import com.tickets.application.port.out.ColaTareas;
import com.tickets.application.port.out.FiltroTickets;
import com.tickets.application.port.out.IndexadorBusqueda;
import com.tickets.application.port.out.TicketRepository;
import com.tickets.application.port.out.TipoTarea;
import com.tickets.domain.Actor;
import com.tickets.domain.Rol;
import com.tickets.domain.Ticket;
import com.tickets.domain.exception.AccesoDenegadoException;
import com.tickets.domain.exception.SolicitudInvalidaException;
import com.tickets.shared.log.Contexto;
import com.tickets.shared.log.Log;

import java.util.Map;

/** Reconstruye el indice: encola una tarea INDEXAR por cada ticket (las procesa el worker, con reintentos). */
public class ReindexarService implements ReindexarBusquedaUseCase {

    private static final int TAMANO_PAGINA = 200;

    private final TicketRepository tickets;
    private final ColaTareas cola;
    private final IndexadorBusqueda indexador;   // null cuando SEARCH_ENGINE=mysql

    public ReindexarService(TicketRepository tickets, ColaTareas cola, IndexadorBusqueda indexador) {
        this.tickets = tickets;
        this.cola = cola;
        this.indexador = indexador;
    }

    @Override
    public int reindexar(Actor actor, boolean recrearIndice) {
        if (actor.rol() != Rol.ADMIN) {
            throw new AccesoDenegadoException("Solo un ADMIN puede reindexar la búsqueda");
        }
        if (indexador == null) {
            throw new SolicitudInvalidaException("La indexación no está habilitada (SEARCH_ENGINE=mysql)");
        }
        if (recrearIndice) {
            indexador.recrearIndice();
        }
        int programadas = 0;
        Pagina<Ticket> pagina;
        int numero = 0;
        do {
            pagina = tickets.buscar(new FiltroTickets(null, null, null, null), numero++, TAMANO_PAGINA);
            for (Ticket t : pagina.contenido()) {
                cola.encolar(TipoTarea.INDEXAR, t.getId(), Contexto.requestId());
                programadas++;
            }
        } while ((long) numero * TAMANO_PAGINA < pagina.totalElementos());
        Log.info("Reindexación programada", Map.of("tickets", programadas, "recrearIndice", recrearIndice,
                "actorId", actor.id()));
        return programadas;
    }
}
