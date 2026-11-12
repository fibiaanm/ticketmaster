package com.tickets.application.port.in;

import com.tickets.domain.Actor;
import com.tickets.domain.Ticket;

public interface ReanalizarTicketUseCase {
    Ticket reanalizar(Actor actor, Long ticketId);
}
