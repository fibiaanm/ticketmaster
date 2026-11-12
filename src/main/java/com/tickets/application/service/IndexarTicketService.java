package com.tickets.application.service;

import com.tickets.application.port.out.IndexadorBusqueda;
import com.tickets.application.port.out.TicketRepository;
import com.tickets.domain.Ticket;

import java.util.Optional;

/**
 * Manejador de tareas INDEXAR. Siempre refleja el estado ACTUAL del ticket en MySQL (no el del momento del
 * evento), asi que es idempotente y tolera reordenamientos: si el ticket ya no existe o fue eliminado, sale del indice.
 */
public class IndexarTicketService {

    private final TicketRepository tickets;
    private final IndexadorBusqueda indexador;

    public IndexarTicketService(TicketRepository tickets, IndexadorBusqueda indexador) {
        this.tickets = tickets;
        this.indexador = indexador;
    }

    public void manejar(Long ticketId) {
        Optional<Ticket> ticket = tickets.buscarPorId(ticketId);
        if (ticket.isPresent()) {
            indexador.indexar(ticket.get());
        } else {
            indexador.eliminar(ticketId);
        }
    }
}
