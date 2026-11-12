package com.tickets.application.port.in;

import com.tickets.domain.Actor;
import com.tickets.domain.Ticket;

public interface CambiarEstadoUseCase {
    Ticket cambiarEstado(Actor actor, Long ticketId, String estado);
}
