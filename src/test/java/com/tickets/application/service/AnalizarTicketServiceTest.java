package com.tickets.application.service;

import com.tickets.application.port.out.AnalisisTicketResponse;
import com.tickets.application.port.out.AnalizadorTicketService;
import com.tickets.domain.*;
import com.tickets.support.EjecutorDirecto;
import com.tickets.support.IndexacionEspia;
import com.tickets.support.InMemoryRegistroEventos;
import com.tickets.support.InMemoryTicketRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnalizarTicketServiceTest {

    InMemoryTicketRepository tickets = new InMemoryTicketRepository();
    Clock reloj = Clock.fixed(Instant.parse("2026-03-01T10:00:00Z"), ZoneOffset.UTC);

    IndexacionEspia indexacion = new IndexacionEspia();
    InMemoryRegistroEventos eventos = new InMemoryRegistroEventos();

    AnalizarTicketService servicio(AnalizadorTicketService analizador) {
        return new AnalizarTicketService(tickets, analizador, EjecutorDirecto.transaccion(), indexacion, eventos, reloj);
    }

    Long ticketNuevo() {
        return tickets.guardar(Ticket.nuevo("Problema con internet",
                "Desde esta mañana no puedo conectarme a internet y tengo una reunión", 3L, reloj.instant())).getId();
    }

    @Test
    void clasificaElTicketConElAnalizadorYGuardaElResultado() {
        AnalizadorTicketService analizador = (t, d) -> new AnalisisTicketResponse("CONECTIVIDAD", "ALTA",
                "Usuario sin conexión antes de una reunión importante.", List.of("internet", "conectividad", "reunión"),
                OrigenAnalisis.LLM, "gpt-x", 9L);
        Long id = ticketNuevo();

        servicio(analizador).analizar(id);

        Ticket t = tickets.items.get(id);
        assertThat(t.getEstadoAnalisis()).isEqualTo(EstadoAnalisis.COMPLETADO);
        assertThat(t.getCategoria()).isEqualTo(Categoria.CONECTIVIDAD);
        assertThat(t.getPrioridad()).isEqualTo(Prioridad.ALTA);
        assertThat(t.getResumenIA()).startsWith("Usuario sin conexión");
        assertThat(t.getEtiquetasIA()).containsExactly("internet", "conectividad", "reunión");
        assertThat(t.getAnalizadoPor()).isEqualTo(OrigenAnalisis.LLM);
        assertThat(t.getPromptTemplateId()).isEqualTo(9L);
    }

    @Test
    void sinTrazabilidadSeAsumeOrigenReglas() {
        AnalizadorTicketService analizador = (t, d) -> new AnalisisTicketResponse("OTRO", "BAJA", "x", List.of("a"));
        Long id = ticketNuevo();
        servicio(analizador).analizar(id);
        assertThat(tickets.items.get(id).getAnalizadoPor()).isEqualTo(OrigenAnalisis.REGLAS);
    }

    @Test
    void siElAnalizadorFallaMarcaFallidoYPropagaParaReintentar() {
        AnalizadorTicketService analizador = (t, d) -> { throw new IllegalStateException("boom"); };
        Long id = ticketNuevo();

        assertThatThrownBy(() -> servicio(analizador).analizar(id))
                .isInstanceOf(IllegalStateException.class);
        assertThat(tickets.items.get(id).getEstadoAnalisis()).isEqualTo(EstadoAnalisis.FALLIDO);
    }

    @Test
    void ticketInexistenteOEliminadoSeIgnora() {
        AnalizadorTicketService analizador = (t, d) -> { throw new AssertionError("no debe llamarse"); };
        servicio(analizador).analizar(999L);

        Long id = ticketNuevo();
        tickets.items.get(id).eliminar(reloj.instant());
        servicio(analizador).analizar(id);
    }

    @Test
    void noPisaLosCambiosHechosMientrasElModeloAnalizaba() {
        Long id = ticketNuevo();
        // mientras el modelo "piensa", soporte cambia el estado y el admin asigna un tecnico
        AnalizadorTicketService analizador = (t, d) -> {
            Ticket concurrente = tickets.buscarPorId(id).orElseThrow();
            concurrente.cambiarEstado(EstadoTicket.EN_PROCESO, reloj.instant());
            concurrente.asignarTecnico(new Usuario(5L, "Sol", "s@x.com", "h", Rol.SOPORTE, reloj.instant()), reloj.instant());
            tickets.guardar(concurrente);
            return new AnalisisTicketResponse("HARDWARE", "BAJA", "r", List.of("x"), OrigenAnalisis.LLM, "m", 1L);
        };

        servicio(analizador).analizar(id);

        Ticket t = tickets.items.get(id);
        assertThat(t.getEstadoAnalisis()).isEqualTo(EstadoAnalisis.COMPLETADO);
        assertThat(t.getCategoria()).isEqualTo(Categoria.HARDWARE);
        assertThat(t.getEstado()).isEqualTo(EstadoTicket.EN_PROCESO);   // conservado
        assertThat(t.getTecnicoAsignadoId()).isEqualTo(5L);             // conservado
    }

    @Test
    void cadaCambioDelAnalisisProgramaLaReindexacion() {
        Long id = ticketNuevo();
        servicio((t, d) -> new AnalisisTicketResponse("OTRO", "BAJA", "r", List.of("x"))).analizar(id);
        assertThat(indexacion.programados).hasSize(2); // ANALIZANDO + COMPLETADO
    }

    @Test
    void registraEventoDeAnalisisConTrazabilidadDeModeloYPrompt() {
        Long id = ticketNuevo();
        servicio((t, d) -> new AnalisisTicketResponse("HARDWARE", "ALTA", "r", List.of("x"), OrigenAnalisis.LLM, "gpt-x", 9L)).analizar(id);

        var ev = eventos.deTicket(id, List.of(TipoEvento.ANALISIS_COMPLETADO));
        assertThat(ev).hasSize(1);
        assertThat(ev.get(0).actor()).isNull(); // lo hizo el sistema
        assertThat(ev.get(0).detalle()).containsEntry("analizadoPor", "LLM").containsEntry("modelo", "gpt-x")
                .containsEntry("promptTemplateId", 9L).containsEntry("categoria", "HARDWARE");
    }

    @Test
    void registraEventoSiElAnalisisFalla() {
        Long id = ticketNuevo();
        try { servicio((t, d) -> { throw new IllegalStateException("boom"); }).analizar(id); } catch (RuntimeException ignorada) { }
        assertThat(eventos.deTicket(id, List.of(TipoEvento.ANALISIS_FALLIDO))).hasSize(1);
    }
}
