package com.tickets.adapter.in.web;

import com.tickets.adapter.in.web.dto.*;
import com.tickets.application.port.in.*;
import com.tickets.application.port.out.FiltroTickets;
import com.tickets.application.port.out.Orden;
import com.tickets.application.service.VistaTickets;
import com.tickets.domain.*;
import com.tickets.domain.exception.SolicitudInvalidaException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@RestController
@RequestMapping("/api/tickets")
@Tag(name = "Tickets")
public class TicketController {

    private static final int MAX_TAMANO = 100;
    private static final int MAX_TEXTO = 100;

    private final CrearTicketUseCase crear;
    private final ConsultarTicketsUseCase consultar;
    private final CambiarEstadoUseCase cambiarEstado;
    private final AsignarTecnicoUseCase asignar;
    private final EliminarTicketUseCase eliminar;
    private final ReanalizarTicketUseCase reanalizar;
    private final ConsultarMetricasUseCase metricas;
    private final VistaTickets vista;

    public TicketController(CrearTicketUseCase crear, ConsultarTicketsUseCase consultar,
                            CambiarEstadoUseCase cambiarEstado, AsignarTecnicoUseCase asignar,
                            EliminarTicketUseCase eliminar, ReanalizarTicketUseCase reanalizar,
                            ConsultarMetricasUseCase metricas, VistaTickets vista) {
        this.crear = crear;
        this.consultar = consultar;
        this.cambiarEstado = cambiarEstado;
        this.asignar = asignar;
        this.eliminar = eliminar;
        this.reanalizar = reanalizar;
        this.metricas = metricas;
        this.vista = vista;
    }

    @PostMapping
    @Operation(summary = "Crear un ticket (el análisis de IA se ejecuta según ANALYSIS_MODE)")
    public ResponseEntity<TicketResponse> crear(@AuthenticationPrincipal Actor actor,
                                                @Valid @RequestBody CrearTicketRequest request) {
        Ticket t = crear.crear(actor, new CrearTicketUseCase.Comando(request.titulo(), request.descripcion(),
                request.usuarioId()));
        return ResponseEntity.created(URI.create("/api/tickets/" + t.getId())).body(TicketResponse.desde(vista.de(t)));
    }

    @GetMapping
    @Operation(summary = "Listar tickets: filtros, búsqueda de texto (q), rango de fechas, orden y paginación",
            description = "sort = campo[,asc|desc] con campo en id, fechaCreacion, fechaActualizacion, prioridad "
                    + "(por gravedad) o titulo. desde/hasta filtran fechaCreacion (ISO-8601 UTC, p. ej. "
                    + "2026-07-30T00:00:00). Con q y sin sort, el orden es por relevancia.")
    public PaginaResponse<TicketResponse> listar(
            @AuthenticationPrincipal Actor actor,
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) String categoria,
            @RequestParam(required = false) String prioridad,
            @RequestParam(required = false) Long usuarioId,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime hasta,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (page < 0 || size < 1 || size > MAX_TAMANO) {
            throw new SolicitudInvalidaException("Paginación inválida: page >= 0 y size entre 1 y " + MAX_TAMANO);
        }
        if (desde != null && hasta != null && desde.isAfter(hasta)) {
            throw new SolicitudInvalidaException("El rango de fechas es inválido: 'desde' es posterior a 'hasta'");
        }
        if (q != null && q.strip().length() > MAX_TEXTO) {
            throw new SolicitudInvalidaException("La búsqueda no puede superar los " + MAX_TEXTO + " caracteres");
        }
        FiltroTickets filtro = new FiltroTickets(
                estado == null ? null : EnumParser.parsear(EstadoTicket.class, estado, "Estado inválido"),
                categoria == null ? null : EnumParser.parsear(Categoria.class, categoria, "Categoría inválida"),
                prioridad == null ? null : EnumParser.parsear(Prioridad.class, prioridad, "Prioridad inválida"),
                usuarioId, desde == null ? null : desde.toInstant(ZoneOffset.UTC),
                hasta == null ? null : hasta.toInstant(ZoneOffset.UTC),
                q == null || q.isBlank() ? null : q.strip(), Orden.parsear(sort));
        return PaginaResponse.desde(vista.de(consultar.listar(actor, filtro, page, size)).map(TicketResponse::desde));
    }

    @GetMapping("/metricas")
    @Operation(summary = "Métricas de tickets por categoría, prioridad, estado y estado de análisis (SOPORTE, ADMIN)")
    public MetricasResponse metricas(@AuthenticationPrincipal Actor actor) {
        return MetricasResponse.desde(metricas.obtener(actor));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consultar un ticket, incluido el análisis de IA")
    public TicketResponse obtener(@AuthenticationPrincipal Actor actor, @PathVariable Long id) {
        return TicketResponse.desde(vista.de(consultar.obtener(actor, id)));
    }

    @PatchMapping("/{id}/estado")
    @Operation(summary = "Actualizar el estado (SOPORTE, ADMIN)")
    public TicketResponse actualizarEstado(@AuthenticationPrincipal Actor actor, @PathVariable Long id,
                                           @Valid @RequestBody CambiarEstadoRequest request) {
        return TicketResponse.desde(vista.de(cambiarEstado.cambiarEstado(actor, id, request.estado())));
    }

    @PatchMapping("/{id}/asignar")
    @Operation(summary = "Asignar un técnico con rol SOPORTE (ADMIN)")
    public TicketResponse asignar(@AuthenticationPrincipal Actor actor, @PathVariable Long id,
                                  @Valid @RequestBody AsignarTecnicoRequest request) {
        return TicketResponse.desde(vista.de(asignar.asignar(actor, id, request.tecnicoId())));
    }

    @PostMapping("/{id}/reanalizar")
    @Operation(summary = "Volver a analizar el ticket con la IA (ADMIN). Responde 202: el resultado llega por el flujo normal")
    public ResponseEntity<TicketResponse> reanalizar(@AuthenticationPrincipal Actor actor, @PathVariable Long id) {
        return ResponseEntity.accepted().body(TicketResponse.desde(vista.de(reanalizar.reanalizar(actor, id))));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Eliminar un ticket (borrado lógico, ADMIN)")
    public ResponseEntity<Void> eliminar(@AuthenticationPrincipal Actor actor, @PathVariable Long id) {
        eliminar.eliminar(actor, id);
        return ResponseEntity.noContent().build();
    }
}
