package com.tickets.application.port.in;

import com.tickets.domain.Actor;
import com.tickets.domain.Ticket;

public interface CrearTicketUseCase {
    Ticket crear(Actor actor, Comando comando);

    record Comando(String titulo, String descripcion, Long usuarioId) {
    }
}
