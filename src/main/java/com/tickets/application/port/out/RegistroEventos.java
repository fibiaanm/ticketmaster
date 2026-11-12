package com.tickets.application.port.out;

import com.tickets.application.Pagina;
import com.tickets.domain.EventoTicket;
import com.tickets.domain.TipoEvento;

import java.time.Instant;
import java.util.List;

public interface RegistroEventos {
    void registrar(EventoTicket evento);

    /** Eventos de un ticket, del mas antiguo al mas reciente. Si tipos no es vacio, filtra por esos tipos. */
    List<EventoTicket> deTicket(Long ticketId, List<TipoEvento> tipos);

    Pagina<EventoTicket> auditoria(FiltroAuditoria filtro, int pagina, int tamano);

    record FiltroAuditoria(Long ticketId, TipoEvento tipo, Long actorId, Instant desde, Instant hasta) {
    }
}
