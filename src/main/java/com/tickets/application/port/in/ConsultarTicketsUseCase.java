package com.tickets.application.port.in;

import com.tickets.application.Pagina;
import com.tickets.application.port.out.FiltroTickets;
import com.tickets.domain.Actor;
import com.tickets.domain.Ticket;

public interface ConsultarTicketsUseCase {
    Pagina<Ticket> listar(Actor actor, FiltroTickets filtro, int pagina, int tamano);

    Ticket obtener(Actor actor, Long id);
}
