package com.tickets.application.port.out;

import com.tickets.application.Pagina;
import com.tickets.domain.Ticket;

import java.util.List;
import java.util.Optional;

public interface TicketRepository {
    Ticket guardar(Ticket ticket);

    /** Excluye tickets eliminados (borrado logico). */
    Optional<Ticket> buscarPorId(Long id);

    /** Listado en MySQL (fuente de la verdad). */
    Pagina<Ticket> buscar(FiltroTickets filtro, int pagina, int tamano);

    /** Tickets no eliminados con esos ids, en el mismo orden de los ids recibidos. */
    List<Ticket> buscarPorIds(List<Long> ids);
}
