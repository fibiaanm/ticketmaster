package com.tickets.application.service;

import com.tickets.application.Pagina;
import com.tickets.application.port.in.CrearTicketUseCase.Comando;
import com.tickets.application.port.out.FiltroTickets;
import com.tickets.domain.*;
import com.tickets.domain.exception.*;
import com.tickets.support.DisparadorEspia;
import com.tickets.support.EjecutorDirecto;
import com.tickets.support.IndexacionEspia;
import com.tickets.support.InMemoryRegistroEventos;
import com.tickets.support.InMemoryTicketRepository;
import com.tickets.support.InMemoryUsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TicketServiceTest {

    static final Actor ADMIN = new Actor(1L, Rol.ADMIN);
    static final Actor SOPORTE = new Actor(2L, Rol.SOPORTE);
    static final Actor USUARIO = new Actor(3L, Rol.USUARIO);
    static final Actor OTRO_USUARIO = new Actor(4L, Rol.USUARIO);

    InMemoryTicketRepository tickets = new InMemoryTicketRepository();
    InMemoryUsuarioRepository usuarios = new InMemoryUsuarioRepository()
            .con(1, Rol.ADMIN).con(2, Rol.SOPORTE).con(3, Rol.USUARIO).con(4, Rol.USUARIO);
    DisparadorEspia disparador = new DisparadorEspia();
    IndexacionEspia indexacion = new IndexacionEspia();
    InMemoryRegistroEventos eventos = new InMemoryRegistroEventos();
    Clock reloj = Clock.fixed(Instant.parse("2026-03-01T10:00:00Z"), ZoneOffset.UTC);
    TicketService service;

    @BeforeEach
    void setUp() {
        service = new TicketService(tickets, tickets::buscar, usuarios, disparador, indexacion, eventos, EjecutorDirecto.transaccion(), reloj);
    }

    Comando comando(Long usuarioId) {
        return new Comando("Problema con internet", "Desde esta mañana no puedo conectarme a internet", usuarioId);
    }

    // ---- crear
    @Test
    void creaElTicketLoGuardaYDisparaElAnalisis() {
        Ticket t = service.crear(USUARIO, comando(3L));

        assertThat(t.getId()).isNotNull();
        assertThat(t.getEstado()).isEqualTo(EstadoTicket.ABIERTO);
        assertThat(t.getSolicitanteId()).isEqualTo(3L);
        assertThat(tickets.items).hasSize(1);
        assertThat(disparador.registrados).containsExactly(t.getId());
        assertThat(disparador.ejecutados).containsExactly(t.getId());
        assertThat(indexacion.programados).containsExactly(t.getId());
    }

    @Test
    void usuarioInexistenteDa404() {
        assertThatThrownBy(() -> service.crear(ADMIN, comando(99L)))
                .isInstanceOf(RecursoNoEncontradoException.class).hasMessageContaining("99");
        assertThat(tickets.items).isEmpty();
    }

    @Test
    void unUsuarioNoPuedeCrearTicketsAMombreDeOtro() {
        assertThatThrownBy(() -> service.crear(USUARIO, comando(4L))).isInstanceOf(AccesoDenegadoException.class);
    }

    @Test
    void adminPuedeCrearEnNombreDeUnUsuario() {
        assertThat(service.crear(ADMIN, comando(3L)).getSolicitanteId()).isEqualTo(3L);
    }

    @Test
    void siFallaElDisparoElTicketQuedaGuardadoConAnalisisFallido() {
        disparador.fallaAlEjecutar = true;
        Ticket t = service.crear(USUARIO, comando(3L));
        assertThat(tickets.items.get(t.getId()).getEstadoAnalisis()).isEqualTo(EstadoAnalisis.FALLIDO);
    }

    // ---- consultar
    @Test
    void consultarTicketInexistenteDa404() {
        assertThatThrownBy(() -> service.obtener(ADMIN, 15L))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessage("No se encontró el ticket con id 15");
    }

    @Test
    void unUsuarioNoVeLosTicketsDeOtroYRecibe404() {
        Ticket t = service.crear(USUARIO, comando(3L));
        assertThatThrownBy(() -> service.obtener(OTRO_USUARIO, t.getId()))
                .isInstanceOf(RecursoNoEncontradoException.class);
        assertThat(service.obtener(SOPORTE, t.getId())).isNotNull();
        assertThat(service.obtener(USUARIO, t.getId())).isNotNull();
    }

    @Test
    void listarAUnUsuarioSoloLeDevuelveLosSuyosAunqueFiltreOtroSolicitante() {
        service.crear(USUARIO, comando(3L));
        service.crear(OTRO_USUARIO, comando(4L));

        Pagina<Ticket> propios = service.listar(USUARIO, new FiltroTickets(null, null, null, 4L), 0, 20);
        assertThat(propios.contenido()).hasSize(1).allMatch(t -> t.getSolicitanteId().equals(3L));
        assertThat(service.listar(SOPORTE, new FiltroTickets(null, null, null, null), 0, 20).totalElementos()).isEqualTo(2);
    }

    @Test
    void listarFiltraPorEstado() {
        Ticket t = service.crear(USUARIO, comando(3L));
        service.crear(USUARIO, comando(3L));
        service.cambiarEstado(SOPORTE, t.getId(), "EN_PROCESO");

        var r = service.listar(SOPORTE, new FiltroTickets(EstadoTicket.EN_PROCESO, null, null, null), 0, 20);
        assertThat(r.contenido()).hasSize(1);
    }

    // ---- estado
    @Test
    void soporteActualizaElEstado() {
        Ticket t = service.crear(USUARIO, comando(3L));
        Ticket actualizado = service.cambiarEstado(SOPORTE, t.getId(), "EN_PROCESO");
        assertThat(actualizado.getEstado()).isEqualTo(EstadoTicket.EN_PROCESO);
    }

    @Test
    void estadoInvalidoDa400() {
        Ticket t = service.crear(USUARIO, comando(3L));
        assertThatThrownBy(() -> service.cambiarEstado(SOPORTE, t.getId(), "VOLANDO"))
                .isInstanceOf(SolicitudInvalidaException.class).hasMessageContaining("Estado inválido");
    }

    @Test
    void unUsuarioNoPuedeCambiarEstados() {
        Ticket t = service.crear(USUARIO, comando(3L));
        assertThatThrownBy(() -> service.cambiarEstado(USUARIO, t.getId(), "EN_PROCESO"))
                .isInstanceOf(AccesoDenegadoException.class);
    }

    // ---- asignar
    @Test
    void adminAsignaUnTecnicoDeSoporte() {
        Ticket t = service.crear(USUARIO, comando(3L));
        assertThat(service.asignar(ADMIN, t.getId(), 2L).getTecnicoAsignadoId()).isEqualTo(2L);
    }

    @Test
    void asignarUnTecnicoInvalidoFalla() {
        Ticket t = service.crear(USUARIO, comando(3L));
        assertThatThrownBy(() -> service.asignar(ADMIN, t.getId(), 3L)).isInstanceOf(TecnicoInvalidoException.class);
        assertThatThrownBy(() -> service.asignar(ADMIN, t.getId(), 1L)).isInstanceOf(TecnicoInvalidoException.class);
    }

    @Test
    void asignarUnTecnicoInexistenteDa404() {
        Ticket t = service.crear(USUARIO, comando(3L));
        assertThatThrownBy(() -> service.asignar(ADMIN, t.getId(), 77L)).isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    void soloAdminAsigna() {
        Ticket t = service.crear(USUARIO, comando(3L));
        assertThatThrownBy(() -> service.asignar(SOPORTE, t.getId(), 2L)).isInstanceOf(AccesoDenegadoException.class);
    }

    // ---- eliminar
    @Test
    void adminEliminaLogicamente() {
        Ticket t = service.crear(USUARIO, comando(3L));
        service.eliminar(ADMIN, t.getId());
        assertThat(tickets.items.get(t.getId()).estaEliminado()).isTrue();
        assertThatThrownBy(() -> service.obtener(ADMIN, t.getId())).isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    void soloAdminElimina() {
        Ticket t = service.crear(USUARIO, comando(3L));
        assertThatThrownBy(() -> service.eliminar(SOPORTE, t.getId())).isInstanceOf(AccesoDenegadoException.class);
        assertThatThrownBy(() -> service.eliminar(USUARIO, t.getId())).isInstanceOf(AccesoDenegadoException.class);
    }

    // ---- indexacion y concurrencia
    @Test
    void cadaCambioProgramaLaReindexacion() {
        Ticket t = service.crear(USUARIO, comando(3L));
        indexacion.programados.clear();
        service.cambiarEstado(SOPORTE, t.getId(), "EN_PROCESO");
        service.asignar(ADMIN, t.getId(), 2L);
        service.eliminar(ADMIN, t.getId());
        assertThat(indexacion.programados).containsExactly(t.getId(), t.getId(), t.getId());
    }

    @Test
    void unConflictoDeConcurrenciaSeReintentaConDatosFrescos() {
        Ticket t = service.crear(USUARIO, comando(3L));
        int[] fallos = {1};
        var conflictivo = new InMemoryTicketRepository() {
            @Override public Ticket guardar(Ticket x) {
                if (x.getId() != null && fallos[0]-- > 0) throw new ConflictoConcurrenciaException("otro proceso");
                return super.guardar(x);
            }
        };
        conflictivo.items.putAll(tickets.items);
        var s = new TicketService(conflictivo, conflictivo::buscar, usuarios, disparador, indexacion, eventos, EjecutorDirecto.transaccion(), reloj);

        assertThat(s.cambiarEstado(SOPORTE, t.getId(), "EN_PROCESO").getEstado()).isEqualTo(EstadoTicket.EN_PROCESO);
    }

    @Test
    void siElConflictoPersisteSeRindeConConflicto() {
        Ticket t = service.crear(USUARIO, comando(3L));
        var siempreConflicto = new InMemoryTicketRepository() {
            @Override public Ticket guardar(Ticket x) { throw new ConflictoConcurrenciaException("otro proceso"); }
        };
        siempreConflicto.items.putAll(tickets.items);
        var s = new TicketService(siempreConflicto, siempreConflicto::buscar, usuarios, disparador, indexacion, eventos, EjecutorDirecto.transaccion(), reloj);

        assertThatThrownBy(() -> s.cambiarEstado(SOPORTE, t.getId(), "EN_PROCESO"))
                .isInstanceOf(ConflictoConcurrenciaException.class);
    }

    // ---- historial / auditoria
    @Test
    void cadaAccionRegistraUnEventoConActorYDetalle() {
        Ticket t = service.crear(USUARIO, comando(3L));
        service.cambiarEstado(SOPORTE, t.getId(), "EN_PROCESO");
        service.asignar(ADMIN, t.getId(), 2L);
        service.eliminar(ADMIN, t.getId());

        var ev = eventos.deTicket(t.getId(), java.util.List.of());
        assertThat(ev).extracting(e -> e.tipo().name())
                .containsExactly("TICKET_CREADO", "ESTADO_CAMBIADO", "TECNICO_ASIGNADO", "TICKET_ELIMINADO");
        assertThat(ev.get(0).actor()).isEqualTo(USUARIO);
        assertThat(ev.get(1).actor()).isEqualTo(SOPORTE);
        assertThat(ev.get(1).detalle()).containsEntry("de", "ABIERTO").containsEntry("a", "EN_PROCESO");
        assertThat(ev.get(2).detalle()).containsEntry("nuevo", 2L).containsEntry("anterior", null);
    }

    @Test
    void unaAccionRechazadaNoDejaEventos() {
        Ticket t = service.crear(USUARIO, comando(3L));
        assertThatThrownBy(() -> service.cambiarEstado(SOPORTE, t.getId(), "RESUELTO")).isInstanceOf(SolicitudInvalidaException.class);
        assertThatThrownBy(() -> service.asignar(ADMIN, t.getId(), 3L)).isInstanceOf(TecnicoInvalidoException.class);
        assertThat(eventos.deTicket(t.getId(), java.util.List.of())).hasSize(1); // solo TICKET_CREADO
    }

    // ---- reanalisis
    @Test
    void adminPuedeReanalizarUnTicketYaAnalizado() {
        Ticket t = service.crear(USUARIO, comando(3L));
        // simula que el analisis termino
        Ticket analizado = tickets.buscarPorId(t.getId()).orElseThrow();
        analizado.completarAnalisis(Categoria.OTRO, Prioridad.BAJA, "r", java.util.List.of("x"), OrigenAnalisis.REGLAS, "m", null, reloj.instant());
        tickets.guardar(analizado);
        disparador.registrados.clear();
        disparador.ejecutados.clear();

        Ticket r = service.reanalizar(ADMIN, t.getId());

        assertThat(r.getEstadoAnalisis()).isEqualTo(EstadoAnalisis.PENDIENTE);
        assertThat(disparador.registrados).containsExactly(t.getId());
        assertThat(disparador.ejecutados).containsExactly(t.getId());
        assertThat(eventos.deTicket(t.getId(), java.util.List.of(TipoEvento.REANALISIS_SOLICITADO))).hasSize(1);
    }

    @Test
    void noSePuedeReanalizarMientrasHayUnAnalisisEnCurso() {
        Ticket t = service.crear(USUARIO, comando(3L)); // queda PENDIENTE
        assertThatThrownBy(() -> service.reanalizar(ADMIN, t.getId()))
                .isInstanceOf(SolicitudInvalidaException.class).hasMessageContaining("en curso");
    }

    @Test
    void soloAdminReanaliza() {
        Ticket t = service.crear(USUARIO, comando(3L));
        assertThatThrownBy(() -> service.reanalizar(SOPORTE, t.getId())).isInstanceOf(AccesoDenegadoException.class);
        assertThatThrownBy(() -> service.reanalizar(ADMIN, 999L)).isInstanceOf(RecursoNoEncontradoException.class);
    }
}
