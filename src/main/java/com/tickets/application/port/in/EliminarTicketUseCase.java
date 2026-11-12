package com.tickets.application.port.in;

import com.tickets.domain.Actor;

public interface EliminarTicketUseCase {
    void eliminar(Actor actor, Long ticketId);
}
