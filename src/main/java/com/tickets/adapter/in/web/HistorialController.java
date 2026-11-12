package com.tickets.adapter.in.web;

import com.tickets.adapter.in.web.dto.EventoResponse;
import com.tickets.adapter.in.web.dto.PaginaResponse;
import com.tickets.application.port.in.ConsultarHistorialUseCase;
import com.tickets.application.port.out.RegistroEventos.FiltroAuditoria;
import com.tickets.domain.Actor;
import com.tickets.domain.EnumParser;
import com.tickets.domain.TipoEvento;
import com.tickets.domain.exception.SolicitudInvalidaException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@RestController
@RequestMapping("/api")
@Tag(name = "Historial y auditoría")
public class HistorialController {

    private final ConsultarHistorialUseCase historial;

    public HistorialController(ConsultarHistorialUseCase historial) {
        this.historial = historial;
    }

    @GetMapping("/tickets/{id}/historial")
    @Operation(summary = "Historial de cambios de estado de un ticket")
    public List<EventoResponse> historial(@AuthenticationPrincipal Actor actor, @PathVariable Long id) {
        return historial.historialDeEstados(actor, id).stream().map(EventoResponse::desde).toList();
    }

    @GetMapping("/tickets/{id}/linea-tiempo")
    @Operation(summary = "Línea de tiempo completa del ticket: estados, asignaciones, análisis (SOPORTE, ADMIN)")
    public List<EventoResponse> lineaDeTiempo(@AuthenticationPrincipal Actor actor, @PathVariable Long id) {
        return historial.lineaDeTiempo(actor, id).stream().map(EventoResponse::desde).toList();
    }

    @GetMapping("/auditoria")
    @Operation(summary = "Auditoría global de acciones sobre tickets, con filtros (ADMIN)")
    public PaginaResponse<EventoResponse> auditoria(
            @AuthenticationPrincipal Actor actor,
            @RequestParam(required = false) Long ticketId,
            @RequestParam(required = false) String tipo,
            @RequestParam(required = false) Long actorId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime hasta,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new SolicitudInvalidaException("Paginación inválida: page >= 0 y size entre 1 y 100");
        }
        var filtro = new FiltroAuditoria(ticketId,
                tipo == null ? null : EnumParser.parsear(TipoEvento.class, tipo, "Tipo de evento inválido"), actorId,
                desde == null ? null : desde.toInstant(ZoneOffset.UTC),
                hasta == null ? null : hasta.toInstant(ZoneOffset.UTC));
        return PaginaResponse.desde(historial.auditoria(actor, filtro, page, size).map(EventoResponse::desde));
    }
}
