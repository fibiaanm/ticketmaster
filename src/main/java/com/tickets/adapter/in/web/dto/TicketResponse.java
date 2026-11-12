package com.tickets.adapter.in.web.dto;

import com.tickets.application.service.TicketVista;
import com.tickets.domain.Ticket;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Campos y nombres tal como los define el enunciado (seccion 17), mas fechaActualizacion y estadoAnalisis
 * (el cliente necesita saber si el analisis asincrono sigue pendiente).
 */
public record TicketResponse(Long id, String titulo, String descripcion, String categoria, String prioridad,
                             String estado, String resumenIA, List<String> etiquetasIA, UsuarioResumen solicitante,
                             UsuarioResumen tecnicoAsignado, LocalDateTime fechaCreacion,
                             LocalDateTime fechaActualizacion, String estadoAnalisis) {

    public static TicketResponse desde(TicketVista v) {
        Ticket t = v.ticket();
        return new TicketResponse(t.getId(), t.getTitulo(), t.getDescripcion(),
                t.getCategoria() == null ? null : t.getCategoria().name(),
                t.getPrioridad() == null ? null : t.getPrioridad().name(),
                t.getEstado().name(), t.getResumenIA(), t.getEtiquetasIA(),
                UsuarioResumen.desde(v.solicitante()), UsuarioResumen.desde(v.tecnico()),
                local(t.getFechaCreacion()), local(t.getFechaActualizacion()), t.getEstadoAnalisis().name());
    }

    private static LocalDateTime local(Instant i) {
        return LocalDateTime.ofInstant(i, ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS);
    }
}
