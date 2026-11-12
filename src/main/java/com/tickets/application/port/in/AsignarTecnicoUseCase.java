package com.tickets.application.port.in;

import com.tickets.domain.Actor;
import com.tickets.domain.Ticket;

public interface AsignarTecnicoUseCase {
    Ticket asignar(Actor actor, Long ticketId, Long tecnicoId);
}
