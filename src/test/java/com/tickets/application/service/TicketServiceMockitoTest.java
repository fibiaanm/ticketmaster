package com.tickets.application.service;

import com.tickets.application.port.out.BuscadorTickets;
import com.tickets.application.port.out.DisparadorAnalisis;
import com.tickets.application.port.out.EjecutorTransaccional;
import com.tickets.application.port.out.FiltroTickets;
import com.tickets.application.port.out.ProgramadorIndexacion;
import com.tickets.application.port.out.RegistroEventos;
import com.tickets.application.port.out.TicketRepository;
import com.tickets.application.port.out.UsuarioRepository;
import com.tickets.application.port.in.CrearTicketUseCase.Comando;
import com.tickets.domain.Actor;
import com.tickets.domain.EstadoTicket;
import com.tickets.domain.EventoTicket;
import com.tickets.domain.Rol;
import com.tickets.domain.Ticket;
import com.tickets.domain.TipoEvento;
import com.tickets.domain.Usuario;
import com.tickets.domain.exception.AccesoDenegadoException;
import com.tickets.domain.exception.RecursoNoEncontradoException;
import com.tickets.domain.exception.TecnicoInvalidoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Verifica las COLABORACIONES de TicketService con sus puertos (lo que invoca y en que orden), con mocks. */
@ExtendWith(MockitoExtension.class)
class TicketServiceMockitoTest {

    static final Actor ADMIN = new Actor(1L, Rol.ADMIN);
    static final Actor USUARIO = new Actor(3L, Rol.USUARIO);
    static final Instant AHORA = Instant.parse("2026-03-01T10:00:00Z");

    @Mock TicketRepository tickets;
    @Mock BuscadorTickets buscador;
    @Mock UsuarioRepository usuarios;
    @Mock DisparadorAnalisis disparador;
    @Mock ProgramadorIndexacion indexacion;
    @Mock RegistroEventos eventos;
    @Mock EjecutorTransaccional ejecutor;

    TicketService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        // el mock de transaccion ejecuta el bloque tal cual
        lenient().when(ejecutor.ejecutar(any())).thenAnswer(inv -> ((Supplier<Object>) inv.getArgument(0)).get());
        lenient().when(tickets.guardar(any(Ticket.class))).thenAnswer(inv -> {
            Ticket t = inv.getArgument(0);
            return Ticket.reconstruir(t.getId() == null ? 42L : t.getId(), 0L, t.getTitulo(), t.getDescripcion(),
                    t.getCategoria(), t.getPrioridad(), t.getEstado(), t.getSolicitanteId(), t.getTecnicoAsignadoId(),
                    t.getResumenIA(), t.getEtiquetasIA(), t.getEstadoAnalisis(), t.getAnalizadoPor(), t.getModeloIA(),
                    t.getPromptTemplateId(), t.getFechaCreacion(), t.getFechaActualizacion(), t.getEliminadoEn());
        });
        service = new TicketService(tickets, buscador, usuarios, disparador, indexacion, eventos,
                new Transaccion(ejecutor), Clock.fixed(AHORA, ZoneOffset.UTC));
    }

    Ticket existente(Long id) {
        return Ticket.reconstruir(id, 0L, "Titulo largo", "Descripcion suficientemente larga", null, null,
                EstadoTicket.ABIERTO, 3L, null, null, java.util.List.of(),
                com.tickets.domain.EstadoAnalisis.COMPLETADO, null, null, null, AHORA, AHORA, null);
    }

    @Test
    void crearGuardaElTicketRegistraElEventoYProgramaAnalisisEIndexacionDentroDeLaTransaccion() {
        when(usuarios.buscarPorId(3L)).thenReturn(Optional.of(new Usuario(3L, "U", "u@x.com", "h", Rol.USUARIO, AHORA)));
        when(tickets.buscarPorId(42L)).thenReturn(Optional.of(existente(42L)));

        Ticket creado = service.crear(USUARIO, new Comando("Titulo largo", "Descripcion suficientemente larga", 3L));

        assertThat(creado.getId()).isEqualTo(42L);
        var orden = inOrder(ejecutor, tickets, eventos, disparador, indexacion);
        orden.verify(ejecutor).ejecutar(any());
        orden.verify(tickets).guardar(any(Ticket.class));
        orden.verify(eventos).registrar(any(EventoTicket.class));
        orden.verify(disparador).registrar(any(Ticket.class));
        orden.verify(indexacion).programar(42L);
        verify(disparador).ejecutarTrasCommit(any(Ticket.class));   // y el analisis sync, tras el commit

        ArgumentCaptor<EventoTicket> evento = ArgumentCaptor.forClass(EventoTicket.class);
        verify(eventos).registrar(evento.capture());
        assertThat(evento.getValue().tipo()).isEqualTo(TipoEvento.TICKET_CREADO);
        assertThat(evento.getValue().actor()).isEqualTo(USUARIO);
        assertThat(evento.getValue().fecha()).isEqualTo(AHORA);
    }

    @Test
    void crearConUsuarioInexistenteNoTocaNingunaColaboracionDeEscritura() {
        when(usuarios.buscarPorId(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.crear(ADMIN, new Comando("Titulo largo", "Descripcion suficientemente larga", 99L)))
                .isInstanceOf(RecursoNoEncontradoException.class);

        verifyNoInteractions(tickets, eventos, disparador, indexacion, ejecutor);
    }

    @Test
    void unUsuarioQueCreaEnNombreDeOtroSeRechazaAntesDeConsultarNada() {
        assertThatThrownBy(() -> service.crear(USUARIO, new Comando("Titulo largo", "Descripcion suficientemente larga", 4L)))
                .isInstanceOf(AccesoDenegadoException.class);
        verifyNoInteractions(usuarios, tickets, eventos);
    }

    @Test
    void asignarUnTecnicoSinRolSoporteNoGuardaNiRegistraEventos() {
        when(tickets.buscarPorId(7L)).thenReturn(Optional.of(existente(7L)));
        when(usuarios.buscarPorId(3L)).thenReturn(Optional.of(new Usuario(3L, "U", "u@x.com", "h", Rol.USUARIO, AHORA)));

        assertThatThrownBy(() -> service.asignar(ADMIN, 7L, 3L)).isInstanceOf(TecnicoInvalidoException.class);

        verify(tickets, never()).guardar(any());
        verifyNoInteractions(eventos, indexacion);
    }

    @Test
    void listarParaUnUsuarioFuerzaSuPropioAlcanceSinImportarElFiltroPedido() {
        service.listar(USUARIO, new FiltroTickets(null, null, null, 999L), 0, 20);

        ArgumentCaptor<FiltroTickets> filtro = ArgumentCaptor.forClass(FiltroTickets.class);
        verify(buscador).buscar(filtro.capture(), anyInt(), anyInt());
        assertThat(filtro.getValue().usuarioSolicitanteId()).isEqualTo(3L);
    }

    @Test
    void listarParaSoporteRespetaElFiltroTalCual() {
        var filtro = new FiltroTickets(null, null, null, 999L);
        service.listar(new Actor(2L, Rol.SOPORTE), filtro, 0, 20);
        verify(buscador).buscar(filtro, 0, 20);
    }

    @Test
    void cambiarEstadoRegistraElEventoConElAntesYElDespues() {
        when(tickets.buscarPorId(7L)).thenReturn(Optional.of(existente(7L)));

        service.cambiarEstado(new Actor(2L, Rol.SOPORTE), 7L, "en_proceso");

        ArgumentCaptor<EventoTicket> evento = ArgumentCaptor.forClass(EventoTicket.class);
        verify(eventos).registrar(evento.capture());
        assertThat(evento.getValue().tipo()).isEqualTo(TipoEvento.ESTADO_CAMBIADO);
        assertThat(evento.getValue().detalle()).containsEntry("de", "ABIERTO").containsEntry("a", "EN_PROCESO");
        verify(indexacion).programar(7L);
    }
}
