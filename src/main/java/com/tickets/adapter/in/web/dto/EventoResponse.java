package com.tickets.adapter.in.web.dto;

import com.tickets.domain.EventoTicket;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Map;

public record EventoResponse(Long id, Long ticketId, String tipo, ActorResponse actor, String requestId,
                             Map<String, Object> detalle, LocalDateTime fecha) {

    public record ActorResponse(Long id, String rol) {
    }

    public static EventoResponse desde(EventoTicket e) {
        return new EventoResponse(e.id(), e.ticketId(), e.tipo().name(),
                e.actor() == null ? null : new ActorResponse(e.actor().id(), e.actor().rol().name()),
                e.requestId(), e.detalle(),
                LocalDateTime.ofInstant(e.fecha(), ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS));
    }
}
