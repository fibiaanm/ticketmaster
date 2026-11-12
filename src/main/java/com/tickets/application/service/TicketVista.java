package com.tickets.application.service;

import com.tickets.domain.Ticket;
import com.tickets.domain.Usuario;

/** Ticket junto con los datos de los usuarios relacionados (para respuestas completas). */
public record TicketVista(Ticket ticket, Usuario solicitante, Usuario tecnico) {
}
