package com.tickets.application.service;

import com.tickets.application.port.out.RegistroEventos.FiltroAuditoria;
import com.tickets.domain.*;
import com.tickets.domain.exception.AccesoDenegadoException;
import com.tickets.domain.exception.RecursoNoEncontradoException;
import com.tickets.support.InMemoryRegistroEventos;
import com.tickets.support.InMemoryTicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HistorialServiceTest {

    static final Actor ADMIN = new Actor(1L, Rol.ADMIN);
    static final Actor SOPORTE = new Actor(2L, Rol.SOPORTE);
    static final Actor DUENO = new Actor(3L, Rol.USUARIO);
    static final Actor AJENO = new Actor(4L, Rol.USUARIO);

    InMemoryTicketRepository tickets = new InMemoryTicketRepository();
    InMemoryRegistroEventos eventos = new InMemoryRegistroEventos();
    HistorialService service = new HistorialService(tickets, eventos);
    Long ticketId;

    @BeforeEach
    void setUp() {
        ticketId = tickets.guardar(Ticket.nuevo("Titulo largo", "Descripcion suficientemente larga", 3L, Instant.EPOCH)).getId();
        eventos.registrar(EventoTicket.nuevo(ticketId, TipoEvento.TICKET_CREADO, DUENO, "r1", Map.of(), Instant.EPOCH));
        eventos.registrar(EventoTicket.nuevo(ticketId, TipoEvento.ESTADO_CAMBIADO, SOPORTE, "r2",
                Map.of("de", "ABIERTO", "a", "EN_PROCESO"), Instant.EPOCH.plusSeconds(60)));
    }

    @Test
    void elHistorialDeEstadosSoloTraeCambiosDeEstado() {
        assertThat(service.historialDeEstados(DUENO, ticketId)).extracting(EventoTicket::tipo)
                .containsExactly(TipoEvento.ESTADO_CAMBIADO);
    }

    @Test
    void unUsuarioNoVeElHistorialDeTicketsAjenos() {
        assertThatThrownBy(() -> service.historialDeEstados(AJENO, ticketId)).isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> service.historialDeEstados(ADMIN, 999L)).isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    void laLineaDeTiempoEsParaSoporteYAdmin() {
        assertThat(service.lineaDeTiempo(SOPORTE, ticketId)).hasSize(2);
        assertThatThrownBy(() -> service.lineaDeTiempo(DUENO, ticketId)).isInstanceOf(AccesoDenegadoException.class);
    }

    @Test
    void laAuditoriaGlobalEsSoloParaAdminYFiltra() {
        var todos = service.auditoria(ADMIN, new FiltroAuditoria(null, null, null, null, null), 0, 20);
        assertThat(todos.totalElementos()).isEqualTo(2);
        assertThat(service.auditoria(ADMIN, new FiltroAuditoria(null, TipoEvento.TICKET_CREADO, null, null, null), 0, 20)
                .contenido()).hasSize(1);
        assertThat(service.auditoria(ADMIN, new FiltroAuditoria(null, null, SOPORTE.id(), null, null), 0, 20)
                .contenido()).hasSize(1);
        assertThatThrownBy(() -> service.auditoria(SOPORTE, new FiltroAuditoria(null, null, null, null, null), 0, 20))
                .isInstanceOf(AccesoDenegadoException.class);
    }
}
