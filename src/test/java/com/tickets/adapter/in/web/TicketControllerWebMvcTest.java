package com.tickets.adapter.in.web;

import com.tickets.application.Pagina;
import com.tickets.application.port.in.*;
import com.tickets.application.port.out.FiltroTickets;
import com.tickets.application.service.Limitador;
import com.tickets.application.service.TicketVista;
import com.tickets.application.service.VistaTickets;
import com.tickets.config.RateLimitProperties;
import com.tickets.domain.*;
import com.tickets.domain.exception.RecursoNoEncontradoException;
import com.tickets.security.SecurityConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Prueba de la capa web aislada: controller + validaciones + manejo de errores + seguridad, con casos de uso simulados. */
@WebMvcTest(TicketController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "ratelimit.enabled=false")
class TicketControllerWebMvcTest {

    @TestConfiguration
    @EnableConfigurationProperties(RateLimitProperties.class)
    static class Propiedades { }

    static final Actor ADMIN = new Actor(1L, Rol.ADMIN);
    static final Actor USUARIO = new Actor(3L, Rol.USUARIO);
    static final Instant T = Instant.parse("2026-07-30T19:30:00Z");

    @Autowired MockMvc mvc;

    @MockitoBean AutenticarUseCase autenticar;
    @MockitoBean CrearTicketUseCase crear;
    @MockitoBean ConsultarTicketsUseCase consultar;
    @MockitoBean CambiarEstadoUseCase cambiarEstado;
    @MockitoBean AsignarTecnicoUseCase asignar;
    @MockitoBean EliminarTicketUseCase eliminar;
    @MockitoBean ReanalizarTicketUseCase reanalizar;
    @MockitoBean ConsultarMetricasUseCase metricas;
    @MockitoBean VistaTickets vista;
    @MockitoBean Limitador limitador;

    Ticket ticket;
    TicketVista vistaTicket;

    @BeforeEach
    void setUp() {
        when(autenticar.autenticarJwt("token-admin")).thenReturn(Optional.of(ADMIN));
        when(autenticar.autenticarJwt("token-usuario")).thenReturn(Optional.of(USUARIO));
        ticket = Ticket.reconstruir(25L, 0L, "Problema con internet", "Desde esta mañana no puedo conectarme a internet",
                Categoria.CONECTIVIDAD, Prioridad.ALTA, EstadoTicket.ABIERTO, 3L, null,
                "Usuario sin conexión antes de una reunión importante.", List.of("internet", "conectividad", "reunión"),
                EstadoAnalisis.COMPLETADO, OrigenAnalisis.LLM, "gpt-x", 1L, T, T, null);
        vistaTicket = new TicketVista(ticket, new Usuario(3L, "Laura Gómez", "l@x.com", "", Rol.USUARIO, T), null);
        when(vista.de(any(Ticket.class))).thenReturn(vistaTicket);
    }

    @Test
    void crearDevuelve201ConLocationYElFormatoExactoDelEnunciado() throws Exception {
        when(crear.crear(any(), any())).thenReturn(ticket);

        mvc.perform(post("/api/tickets").header("Authorization", "Bearer token-usuario")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"titulo\":\"Problema con internet\",\"descripcion\":\"Desde esta mañana no puedo conectarme a internet\",\"usuarioId\":3}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/tickets/25"))
                .andExpect(jsonPath("$.id").value(25))
                .andExpect(jsonPath("$.titulo").value("Problema con internet"))
                .andExpect(jsonPath("$.categoria").value("CONECTIVIDAD"))
                .andExpect(jsonPath("$.prioridad").value("ALTA"))
                .andExpect(jsonPath("$.estado").value("ABIERTO"))
                .andExpect(jsonPath("$.resumenIA").value("Usuario sin conexión antes de una reunión importante."))
                .andExpect(jsonPath("$.etiquetasIA", contains("internet", "conectividad", "reunión")))
                .andExpect(jsonPath("$.solicitante.id").value(3))
                .andExpect(jsonPath("$.solicitante.nombre").value("Laura Gómez"))
                .andExpect(jsonPath("$.tecnicoAsignado").value(nullValue()))
                .andExpect(jsonPath("$.fechaCreacion").value("2026-07-30T19:30:00"));

        // el actor sale de la credencial, no del cuerpo
        ArgumentCaptor<Actor> actor = ArgumentCaptor.forClass(Actor.class);
        verify(crear).crear(actor.capture(), any());
        assertThat(actor.getValue()).isEqualTo(USUARIO);
    }

    @Test
    void unCuerpoInvalidoDa400SinLlamarAlCasoDeUso() throws Exception {
        mvc.perform(post("/api/tickets").header("Authorization", "Bearer token-usuario")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"titulo\":\"abc\",\"descripcion\":\"corta\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message", containsString("título debe tener entre 5 y 100")))
                .andExpect(jsonPath("$.message", containsString("usuarioId es obligatorio")));
        verifyNoInteractions(crear);
    }

    @Test
    void sinCredencialesDa401ConElFormatoDeError() throws Exception {
        mvc.perform(get("/api/tickets/25"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.path").value("/api/tickets/25"));
        verifyNoInteractions(consultar);
    }

    @Test
    void ticketInexistenteSeTraduceA404() throws Exception {
        when(consultar.obtener(any(), eq(15L))).thenThrow(RecursoNoEncontradoException.ticket(15L));

        mvc.perform(get("/api/tickets/15").header("Authorization", "Bearer token-admin"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("No se encontró el ticket con id 15"))
                .andExpect(jsonPath("$.path").value("/api/tickets/15"));
    }

    @Test
    void unErrorInesperadoDa500SinFiltrarDetallesInternos() throws Exception {
        when(consultar.obtener(any(), anyLong())).thenThrow(new IllegalStateException("clave de la BD: secreto123"));

        mvc.perform(get("/api/tickets/1").header("Authorization", "Bearer token-admin"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Error interno del servidor"))
                .andExpect(content().string(not(containsString("secreto123"))));
    }

    @Test
    void listarTraduceLosParametrosAUnFiltroYPaginaLaRespuesta() throws Exception {
        when(consultar.listar(any(), any(), anyInt(), anyInt())).thenReturn(new Pagina<>(List.of(ticket), 1, 5, 11));
        when(vista.de(any(Pagina.class))).thenReturn(new Pagina<>(List.of(vistaTicket), 1, 5, 11));

        mvc.perform(get("/api/tickets?estado=abierto&prioridad=ALTA&sort=prioridad,desc&page=1&size=5")
                        .param("q", " internet ").header("Authorization", "Bearer token-admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(11))
                .andExpect(jsonPath("$.totalPaginas").value(3))
                .andExpect(jsonPath("$.contenido", hasSize(1)));

        ArgumentCaptor<FiltroTickets> filtro = ArgumentCaptor.forClass(FiltroTickets.class);
        verify(consultar).listar(eq(ADMIN), filtro.capture(), eq(1), eq(5));
        assertThat(filtro.getValue().estado()).isEqualTo(EstadoTicket.ABIERTO);
        assertThat(filtro.getValue().prioridad()).isEqualTo(Prioridad.ALTA);
        assertThat(filtro.getValue().texto()).isEqualTo("internet");
        assertThat(filtro.getValue().orden().campo().nombre).isEqualTo("prioridad");
    }

    @Test
    void parametrosDeListadoInvalidosDan400SinLlamarAlCasoDeUso() throws Exception {
        for (String url : List.of("/api/tickets?estado=VOLANDO", "/api/tickets?sort=password", "/api/tickets?size=500",
                "/api/tickets?desde=2026-02-01T00:00:00&hasta=2026-01-01T00:00:00")) {
            mvc.perform(get(url).header("Authorization", "Bearer token-admin")).andExpect(status().isBadRequest());
        }
        verifyNoInteractions(consultar);
    }

    @Test
    void eliminarDevuelve204() throws Exception {
        mvc.perform(delete("/api/tickets/25").header("Authorization", "Bearer token-admin"))
                .andExpect(status().isNoContent());
        verify(eliminar).eliminar(ADMIN, 25L);
    }
}
