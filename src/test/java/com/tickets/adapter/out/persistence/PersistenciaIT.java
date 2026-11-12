package com.tickets.adapter.out.persistence;

import com.tickets.application.port.out.ColaTareas;
import com.tickets.application.port.out.Tarea;
import com.tickets.application.port.out.TipoTarea;
import com.tickets.application.port.out.FiltroTickets;
import com.tickets.application.port.out.MetricasRepository;
import com.tickets.application.port.out.MetricasTickets;
import com.tickets.application.port.out.Orden;
import com.tickets.application.port.out.PromptTemplateRepository;
import com.tickets.application.port.out.TicketRepository;
import com.tickets.application.port.out.UsuarioRepository;
import com.tickets.domain.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class PersistenciaIT {

    @Autowired TicketRepository tickets;
    @Autowired UsuarioRepository usuarios;
    @Autowired PromptTemplateRepository prompts;
    @Autowired ColaTareas cola;
    @Autowired MetricasRepository metricas;
    @Autowired JdbcTemplate jdbc;

    Instant ahora = Instant.now().truncatedTo(ChronoUnit.MILLIS);
    Usuario solicitante;
    Usuario tecnico;

    @BeforeEach
    void preparar() {
        com.tickets.support.TestDb.limpiar(jdbc);
        solicitante = usuarios.guardar(new Usuario(null, "Ana", "ana@x.com", "hash", Rol.USUARIO, ahora));
        tecnico = usuarios.guardar(new Usuario(null, "Sol", "sol@x.com", "hash", Rol.SOPORTE, ahora));
    }

    @AfterEach
    void despues() {
        // los prompts de prueba no deben contaminar el arranque de otros contextos (versiones inmutables)
        com.tickets.support.TestDb.limpiar(jdbc);
    }

    Ticket nuevo(String titulo) {
        return tickets.guardar(Ticket.nuevo(titulo, "Descripcion lo bastante larga para el ticket", solicitante.id(), ahora));
    }

    @Test
    void guardaYRecuperaUnTicketCompletoConAnalisis() {
        Ticket t = nuevo("Problema con internet");
        t.completarAnalisis(Categoria.CONECTIVIDAD, Prioridad.ALTA, "Sin conexión", List.of("internet", "reunión"),
                OrigenAnalisis.LLM, "gpt-x", null, ahora);
        t.asignarTecnico(tecnico, ahora);
        tickets.guardar(t);

        Ticket leido = tickets.buscarPorId(t.getId()).orElseThrow();
        assertThat(leido.getTitulo()).isEqualTo("Problema con internet");
        assertThat(leido.getCategoria()).isEqualTo(Categoria.CONECTIVIDAD);
        assertThat(leido.getPrioridad()).isEqualTo(Prioridad.ALTA);
        assertThat(leido.getEtiquetasIA()).containsExactly("internet", "reunión");
        assertThat(leido.getEstadoAnalisis()).isEqualTo(EstadoAnalisis.COMPLETADO);
        assertThat(leido.getTecnicoAsignadoId()).isEqualTo(tecnico.id());
        assertThat(leido.getSolicitanteId()).isEqualTo(solicitante.id());
        assertThat(leido.getFechaCreacion()).isEqualTo(ahora);
    }

    @Test
    void elBorradoLogicoOcultaElTicketDeConsultasYListados() {
        Ticket t = nuevo("A borrar");
        t.eliminar(ahora);
        tickets.guardar(t);

        assertThat(tickets.buscarPorId(t.getId())).isEmpty();
        assertThat(tickets.buscar(new FiltroTickets(null, null, null, null), 0, 20).totalElementos()).isZero();
        // sigue en la tabla
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ticket WHERE id = ?", Integer.class, t.getId())).isEqualTo(1);
    }

    @Test
    void filtraPorEstadoPrioridadCategoriaYSolicitante() {
        Ticket a = nuevo("Uno");
        a.completarAnalisis(Categoria.HARDWARE, Prioridad.ALTA, "r", List.of("x"), OrigenAnalisis.REGLAS, "m", null, ahora);
        tickets.guardar(a);
        Ticket b = nuevo("Dos");
        b.cambiarEstado(EstadoTicket.EN_PROCESO, ahora);
        tickets.guardar(b);
        nuevo("Tres");

        assertThat(tickets.buscar(new FiltroTickets(null, Categoria.HARDWARE, null, null), 0, 20).contenido())
                .extracting(Ticket::getTitulo).containsExactly("Uno");
        assertThat(tickets.buscar(new FiltroTickets(EstadoTicket.EN_PROCESO, null, null, null), 0, 20).contenido())
                .extracting(Ticket::getTitulo).containsExactly("Dos");
        assertThat(tickets.buscar(new FiltroTickets(EstadoTicket.ABIERTO, null, Prioridad.ALTA, solicitante.id()), 0, 20).contenido())
                .extracting(Ticket::getTitulo).containsExactly("Uno");
        assertThat(tickets.buscar(new FiltroTickets(null, null, null, tecnico.id()), 0, 20).totalElementos()).isZero();
    }

    @Test
    void pagina() {
        for (int i = 0; i < 5; i++) nuevo("Ticket " + i);
        var p = tickets.buscar(new FiltroTickets(null, null, null, null), 1, 2);
        assertThat(p.contenido()).hasSize(2);
        assertThat(p.totalElementos()).isEqualTo(5);
        assertThat(p.pagina()).isEqualTo(1);
    }

    @Test
    void elBloqueoOptimistaDetectaEscriturasObsoletas() {
        Ticket t = nuevo("Concurrente");
        Ticket copiaA = tickets.buscarPorId(t.getId()).orElseThrow();
        Ticket copiaB = tickets.buscarPorId(t.getId()).orElseThrow();

        copiaA.cambiarEstado(EstadoTicket.EN_PROCESO, ahora);
        tickets.guardar(copiaA);

        copiaB.eliminar(ahora);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> tickets.guardar(copiaB))
                .isInstanceOf(com.tickets.domain.exception.ConflictoConcurrenciaException.class);
        assertThat(tickets.buscarPorId(t.getId()).orElseThrow().getVersion()).isEqualTo(1L);
    }

    @Test
    void buscaUsuariosPorVariosIds() {
        assertThat(usuarios.buscarPorIds(List.of(solicitante.id(), tecnico.id(), 99999L))).hasSize(2);
        assertThat(usuarios.buscarPorIds(List.of())).isEmpty();
    }

    @Test
    void usuariosPorCorreoIgnoraMayusculas() {
        assertThat(usuarios.buscarPorCorreo("ANA@x.com")).isPresent();
        assertThat(usuarios.buscarPorCorreo("nadie@x.com")).isEmpty();
    }

    @Test
    void promptsSoloUnoActivoPorNombre() {
        prompts.guardar(new PromptTemplate(null, "ticket-analysis", 1, "s1", "u1", false, "h1"));
        prompts.guardar(new PromptTemplate(null, "ticket-analysis", 2, "s2", "u2", false, "h2"));
        prompts.activarExclusivamente("ticket-analysis", 1);
        prompts.activarExclusivamente("ticket-analysis", 2);

        assertThat(prompts.buscarActivo("ticket-analysis").orElseThrow().version()).isEqualTo(2);
        assertThat(prompts.buscar("ticket-analysis", 1).orElseThrow().activo()).isFalse();
        assertThat(prompts.buscar("ticket-analysis", 3)).isEmpty();
    }

    @Test
    void colaReclamaEnOrdenYNoEntregaDosVecesElMismoTrabajo() {
        Ticket t1 = nuevo("Uno");
        Ticket t2 = nuevo("Dos");
        cola.encolar(TipoTarea.ANALIZAR, t1.getId(), "req-1");
        cola.encolar(TipoTarea.INDEXAR, t2.getId(), "req-2");

        Tarea j1 = cola.tomarSiguiente().orElseThrow();
        Tarea j2 = cola.tomarSiguiente().orElseThrow();

        assertThat(j1.ticketId()).isEqualTo(t1.getId());
        assertThat(j1.requestId()).isEqualTo("req-1");
        assertThat(j1.tipo()).isEqualTo(TipoTarea.ANALIZAR);
        assertThat(j2.tipo()).isEqualTo(TipoTarea.INDEXAR);
        assertThat(j1.intentos()).isEqualTo(1);
        assertThat(j2.ticketId()).isEqualTo(t2.getId());
        assertThat(cola.tomarSiguiente()).isEmpty();

        cola.completar(j1.id());
        cola.marcarFallida(j2.id(), "x");
        assertThat(cola.tomarSiguiente()).isEmpty();
    }

    @Test
    void colaRespetaDisponibleDesdeAlReprogramar() {
        Ticket t = nuevo("Uno");
        cola.encolar(TipoTarea.ANALIZAR, t.getId(), null);
        Tarea j = cola.tomarSiguiente().orElseThrow();

        cola.reprogramar(j.id(), "fallo", Instant.now().plus(1, ChronoUnit.HOURS));
        assertThat(cola.tomarSiguiente()).isEmpty();

        cola.reprogramar(j.id(), "fallo", Instant.now().minusSeconds(5));
        Optional<Tarea> otra = cola.tomarSiguiente();
        assertThat(otra).isPresent();
        assertThat(otra.get().intentos()).isEqualTo(2);
    }

    // ---------- busqueda de texto, fechas y orden
    Ticket conPrioridad(String titulo, Prioridad prioridad, Instant creado) {
        Ticket t = tickets.guardar(Ticket.nuevo(titulo, "Descripcion lo bastante larga para el ticket", solicitante.id(), creado));
        t.completarAnalisis(Categoria.OTRO, prioridad, "r", List.of("x"), OrigenAnalisis.REGLAS, "m", null, creado);
        return tickets.guardar(t);
    }

    FiltroTickets filtro(String texto, Instant desde, Instant hasta, Orden orden) {
        return new FiltroTickets(null, null, null, null, desde, hasta, texto, orden);
    }

    @Test
    void buscaTextoEnTituloYDescripcionIgnorandoTildesYMayusculas() {
        tickets.guardar(Ticket.nuevo("Reunión sin conexión", "El wifi de la sala no funciona desde hoy", solicitante.id(), ahora));
        tickets.guardar(Ticket.nuevo("Otro asunto", "La impresora del piso dos atasca el papel siempre", solicitante.id(), ahora));

        assertThat(tickets.buscar(filtro("REUNION", null, null, null), 0, 20).contenido()).hasSize(1);
        assertThat(tickets.buscar(filtro("impresora", null, null, null), 0, 20).contenido()).hasSize(1);
        assertThat(tickets.buscar(filtro("inexistente", null, null, null), 0, 20).contenido()).isEmpty();
    }

    @Test
    void losComodinesDelLikeSeTratanComoTextoLiteral() {
        tickets.guardar(Ticket.nuevo("Ticket normal", "Descripcion lo bastante larga para el ticket", solicitante.id(), ahora));
        assertThat(tickets.buscar(filtro("%", null, null, null), 0, 20).contenido()).isEmpty();
        assertThat(tickets.buscar(filtro("_", null, null, null), 0, 20).contenido()).isEmpty();
        assertThat(tickets.buscar(filtro("\\", null, null, null), 0, 20).contenido()).isEmpty();
    }

    @Test
    void filtraPorRangoDeFechasDeCreacion() {
        Instant base = Instant.parse("2026-05-10T10:00:00Z");
        conPrioridad("Viejo uno", Prioridad.BAJA, base.minusSeconds(86400 * 10));
        conPrioridad("Medio uno", Prioridad.BAJA, base);
        conPrioridad("Nuevo uno", Prioridad.BAJA, base.plusSeconds(86400 * 10));

        var r = tickets.buscar(filtro(null, base.minusSeconds(3600), base.plusSeconds(3600), null), 0, 20);
        assertThat(r.contenido()).extracting(Ticket::getTitulo).containsExactly("Medio uno");
        assertThat(tickets.buscar(filtro(null, base, null, null), 0, 20).totalElementos()).isEqualTo(2);
        assertThat(tickets.buscar(filtro(null, null, base, null), 0, 20).totalElementos()).isEqualTo(2);
    }

    @Test
    void ordenaPorGravedadDeLaPrioridadYNoAlfabeticamente() {
        Instant t0 = Instant.parse("2026-05-10T10:00:00Z");
        conPrioridad("Media", Prioridad.MEDIA, t0);
        conPrioridad("Critica", Prioridad.CRITICA, t0.plusSeconds(1));
        conPrioridad("Baja", Prioridad.BAJA, t0.plusSeconds(2));
        conPrioridad("Alta", Prioridad.ALTA, t0.plusSeconds(3));
        tickets.guardar(Ticket.nuevo("Sin analizar", "Descripcion lo bastante larga para el ticket", solicitante.id(), t0));

        var desc = tickets.buscar(filtro(null, null, null, Orden.parsear("prioridad,desc")), 0, 20);
        assertThat(desc.contenido()).extracting(Ticket::getTitulo)
                .containsExactly("Critica", "Alta", "Media", "Baja", "Sin analizar");
        var asc = tickets.buscar(filtro(null, null, null, Orden.parsear("prioridad,asc")), 0, 20);
        assertThat(asc.contenido()).extracting(Ticket::getTitulo)
                .containsExactly("Sin analizar", "Baja", "Media", "Alta", "Critica");
    }

    @Test
    void ordenaPorTituloYFechaYPorDefectoLoMasRecientePrimero() {
        Instant t0 = Instant.parse("2026-05-10T10:00:00Z");
        conPrioridad("Bravo", Prioridad.BAJA, t0);
        conPrioridad("Alfa", Prioridad.BAJA, t0.plusSeconds(10));
        conPrioridad("Charlie", Prioridad.BAJA, t0.plusSeconds(20));

        assertThat(tickets.buscar(filtro(null, null, null, Orden.parsear("titulo")), 0, 20).contenido())
                .extracting(Ticket::getTitulo).containsExactly("Alfa", "Bravo", "Charlie");
        assertThat(tickets.buscar(filtro(null, null, null, null), 0, 20).contenido())
                .extracting(Ticket::getTitulo).containsExactly("Charlie", "Alfa", "Bravo");
    }

    @Test
    void laPaginacionConOrdenEsEstableYElTotalCuentaTodo() {
        Instant t0 = Instant.parse("2026-05-10T10:00:00Z");
        for (int i = 0; i < 5; i++) conPrioridad("Igual " + i, Prioridad.ALTA, t0); // misma prioridad y fecha
        var p0 = tickets.buscar(filtro(null, null, null, Orden.parsear("prioridad,desc")), 0, 2);
        var p1 = tickets.buscar(filtro(null, null, null, Orden.parsear("prioridad,desc")), 1, 2);
        var p2 = tickets.buscar(filtro(null, null, null, Orden.parsear("prioridad,desc")), 2, 2);
        assertThat(p0.totalElementos()).isEqualTo(5);
        assertThat(java.util.stream.Stream.of(p0, p1, p2).flatMap(p -> p.contenido().stream()).map(Ticket::getId).distinct())
                .hasSize(5);
    }

    @Test
    void buscaVariosPorIdConservandoElOrdenYOmitiendoEliminados() {
        Ticket a = nuevo("Uno"), b = nuevo("Dos"), c = nuevo("Tres");
        c.eliminar(ahora);
        tickets.guardar(c);
        assertThat(tickets.buscarPorIds(List.of(b.getId(), a.getId(), c.getId(), 99999L)))
                .extracting(Ticket::getTitulo).containsExactly("Dos", "Uno");
        assertThat(tickets.buscarPorIds(List.of())).isEmpty();
    }

    // ---------- metricas
    @Test
    void calculaMetricasPorCategoriaPrioridadYEstadoExcluyendoEliminados() {
        conPrioridad("Uno", Prioridad.ALTA, ahora);
        Ticket hw = nuevo("Hardware");
        hw.completarAnalisis(Categoria.HARDWARE, Prioridad.ALTA, "r", List.of("x"), OrigenAnalisis.REGLAS, "m", null, ahora);
        hw.cambiarEstado(EstadoTicket.EN_PROCESO, ahora);
        tickets.guardar(hw);
        nuevo("Sin analizar");
        Ticket borrado = conPrioridad("Borrado", Prioridad.BAJA, ahora);
        borrado.eliminar(ahora);
        tickets.guardar(borrado);

        MetricasTickets m = metricas.calcular();

        assertThat(m.total()).isEqualTo(3);
        assertThat(m.porCategoria()).containsEntry("OTRO", 1L).containsEntry("HARDWARE", 1L)
                .containsEntry("SIN_ANALIZAR", 1L).containsEntry("SOFTWARE", 0L);
        assertThat(m.porPrioridad()).containsEntry("ALTA", 2L).containsEntry("BAJA", 0L).containsEntry("SIN_ANALIZAR", 1L);
        assertThat(m.porEstado()).containsEntry("ABIERTO", 2L).containsEntry("EN_PROCESO", 1L).containsEntry("CERRADO", 0L);
        assertThat(m.porEstadoAnalisis()).containsEntry("COMPLETADO", 2L).containsEntry("PENDIENTE", 1L);
        assertThat(m.categoriaPorPrioridad()).contains(new MetricasTickets.Celda("HARDWARE", "ALTA", 1),
                new MetricasTickets.Celda("SIN_ANALIZAR", "SIN_ANALIZAR", 1));
    }
}
