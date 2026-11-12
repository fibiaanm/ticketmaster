package com.tickets.domain;

import java.time.Instant;
import java.util.Map;

/**
 * Hecho inmutable ocurrido sobre un ticket (historial + auditoria).
 * actor == null significa que lo hizo el sistema (p. ej. el worker de analisis).
 */
public record EventoTicket(Long id, Long ticketId, TipoEvento tipo, Actor actor, String requestId,
                           Map<String, Object> detalle, Instant fecha) {

    public static EventoTicket nuevo(Long ticketId, TipoEvento tipo, Actor actor, String requestId,
                                     Map<String, Object> detalle, Instant fecha) {
        return new EventoTicket(null, ticketId, tipo, actor, requestId, detalle == null ? Map.of() : detalle, fecha);
    }
}
