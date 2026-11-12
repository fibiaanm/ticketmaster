package com.tickets.adapter.out.search;

import com.tickets.application.port.out.FiltroTickets;
import com.tickets.application.port.out.Orden;
import com.tickets.domain.*;
import com.tickets.domain.exception.BusquedaNoDisponibleException;
import com.tickets.support.InMemoryTicketRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Contra un OpenSearch real (el del compose). Se omite si no esta disponible. */
class OpenSearchIT {

    static final Instant T0 = Instant.parse("2026-05-10T10:00:00Z");

    OpenSearchCliente cliente;
    OpenSearchIndexador indexador;
    OpenSearchBuscador buscador;
    InMemoryTicketRepository tickets = new InMemoryTicketRepository();
    String indice = "tickets-it-" + UUID.randomUUID();

    @BeforeEach
    void conectar() {
        String url = System.getenv().getOrDefault("OPENSEARCH_URL", "http://localhost:9200");
        cliente = new OpenSearchCliente(url, Duration.ofSeconds(3));
        try {
            cliente.enviar("GET", "/_cluster/health", null);
        } catch (RuntimeException e) {
            Assumptions.assumeTrue(false, "OpenSearch no disponible: prueba omitida");
        }
        indexador = new OpenSearchIndexador(cliente, indice);
        buscador = new OpenSearchBuscador(cliente, tickets, indice);
    }

    @AfterEach
    void limpiar() {
        if (cliente != null) {
            try { cliente.enviar("DELETE", "/" + indice, null, 404); } catch (RuntimeException ignorada) { }
        }
    }

    Ticket ticket(String titulo, String descripcion, Categoria c, Prioridad p, long solicitante, Instant creado) {
        Ticket t = tickets.guardar(Ticket.nuevo(titulo, descripcion, solicitante, creado));
        t.completarAnalisis(c, p, "resumen", List.of("etiqueta"), OrigenAnalisis.REGLAS, "m", null, creado);
        t = tickets.guardar(t);
        indexador.indexar(t);
        return t;
    }

    void refrescar() {
        cliente.enviar("POST", "/" + indice + "/_refresh", null);
    }

    List<String> titulos(FiltroTickets f) {
        refrescar();
        return buscador.buscar(f, 0, 20).contenido().stream().map(Ticket::getTitulo).toList();
    }

    FiltroTickets texto(String q) {
        return new FiltroTickets(null, null, null, null, null, null, q, null);
    }

    @Test
    void crearElIndiceEsIdempotente() {
        indexador.asegurarIndice();
        indexador.asegurarIndice();
        assertThat(new OpenSearchIndexador(cliente, indice).getClass()).isNotNull();
        new OpenSearchIndexador(cliente, indice).asegurarIndice();   // otra instancia sobre el indice existente
    }

    @Test
    void buscaIgnorandoTildesMayusculasPluralesYErratasLeves() {
        ticket("Reunión sin conexión", "No puedo conectarme a internet antes de la reunión", Categoria.CONECTIVIDAD, Prioridad.ALTA, 1, T0);
        ticket("Impresora atascada", "La impresora del segundo piso atasca el papel", Categoria.HARDWARE, Prioridad.BAJA, 1, T0);

        assertThat(titulos(texto("REUNION"))).containsExactly("Reunión sin conexión");       // tildes y mayusculas
        assertThat(titulos(texto("conexiones"))).containsExactly("Reunión sin conexión");    // plural / raiz
        assertThat(titulos(texto("internte"))).containsExactly("Reunión sin conexión");      // errata
        assertThat(titulos(texto("impresoras"))).containsExactly("Impresora atascada");
        assertThat(titulos(texto("inexistente"))).isEmpty();
    }

    @Test
    void ordenaPorRelevanciaCuandoNoSePideOtroOrden() {
        ticket("Otro asunto", "Mencion pasajera de la impresora en una descripcion larga de cosas", Categoria.OTRO, Prioridad.BAJA, 1, T0.plusSeconds(100));
        ticket("Impresora rota", "La impresora no enciende", Categoria.HARDWARE, Prioridad.ALTA, 1, T0);

        assertThat(titulos(texto("impresora"))).containsExactly("Impresora rota", "Otro asunto"); // titulo pesa mas
    }

    @Test
    void aplicaFiltrosFechasYAlcanceDelUsuario() {
        ticket("Uno", "descripcion de prueba uno", Categoria.HARDWARE, Prioridad.ALTA, 1, T0);
        Ticket dos = ticket("Dos", "descripcion de prueba dos", Categoria.SOFTWARE, Prioridad.BAJA, 2, T0.plusSeconds(86400));
        dos.cambiarEstado(EstadoTicket.EN_PROCESO, T0);
        indexador.indexar(tickets.guardar(dos));

        assertThat(titulos(new FiltroTickets(null, Categoria.HARDWARE, null, null))).containsExactly("Uno");
        assertThat(titulos(new FiltroTickets(EstadoTicket.EN_PROCESO, null, null, null))).containsExactly("Dos");
        assertThat(titulos(new FiltroTickets(null, null, Prioridad.ALTA, 1L))).containsExactly("Uno");
        assertThat(titulos(new FiltroTickets(null, null, null, 2L))).containsExactly("Dos");   // alcance de usuario
        assertThat(titulos(new FiltroTickets(null, null, null, null, T0.plusSeconds(3600), null, null, null))).containsExactly("Dos");
        assertThat(titulos(new FiltroTickets(null, null, null, null, null, T0.plusSeconds(3600), null, null))).containsExactly("Uno");
    }

    @Test
    void ordenaPorGravedadYTituloComoMysql() {
        ticket("Media", "descripcion de prueba", Categoria.OTRO, Prioridad.MEDIA, 1, T0);
        ticket("Critica", "descripcion de prueba", Categoria.OTRO, Prioridad.CRITICA, 1, T0.plusSeconds(1));
        ticket("Baja", "descripcion de prueba", Categoria.OTRO, Prioridad.BAJA, 1, T0.plusSeconds(2));
        ticket("Alta", "descripcion de prueba", Categoria.OTRO, Prioridad.ALTA, 1, T0.plusSeconds(3));
        ticket("ÁRBOL", "descripcion de prueba", Categoria.OTRO, Prioridad.BAJA, 1, T0.plusSeconds(4));

        assertThat(titulos(new FiltroTickets(null, null, null, null, null, null, null, Orden.parsear("prioridad,desc"))))
                .startsWith("Critica", "Alta", "Media");
        assertThat(titulos(new FiltroTickets(null, null, null, null, null, null, null, Orden.parsear("titulo,asc"))))
                .containsExactly("Alta", "ÁRBOL", "Baja", "Critica", "Media");   // sin distinguir tildes ni mayusculas
    }

    @Test
    void paginaYReportaElTotal() {
        for (int i = 0; i < 5; i++) ticket("Ticket " + i, "descripcion de prueba", Categoria.OTRO, Prioridad.BAJA, 1, T0.plusSeconds(i));
        refrescar();
        var p1 = buscador.buscar(new FiltroTickets(null, null, null, null), 1, 2);
        assertThat(p1.totalElementos()).isEqualTo(5);
        assertThat(p1.contenido()).hasSize(2);
    }

    @Test
    void losDatosDevueltosSonLosFrescosDeMysqlYLosEliminadosNoAparecen() {
        Ticket t = ticket("Titulo indexado", "descripcion de prueba", Categoria.OTRO, Prioridad.BAJA, 1, T0);
        refrescar();
        // MySQL cambia (el indice aun no lo sabe)
        Ticket cambiado = tickets.buscarPorId(t.getId()).orElseThrow();
        cambiado.cambiarEstado(EstadoTicket.EN_PROCESO, T0);
        tickets.guardar(cambiado);
        assertThat(buscador.buscar(texto("indexado"), 0, 20).contenido().get(0).getEstado()).isEqualTo(EstadoTicket.EN_PROCESO);

        Ticket borrado = tickets.buscarPorId(t.getId()).orElseThrow();
        borrado.eliminar(T0);
        tickets.guardar(borrado);
        assertThat(buscador.buscar(texto("indexado"), 0, 20).contenido()).isEmpty();
    }

    @Test
    void eliminarDelIndiceYRecrearLoVacian() {
        Ticket t = ticket("Para borrar", "descripcion de prueba", Categoria.OTRO, Prioridad.BAJA, 1, T0);
        refrescar();
        assertThat(titulos(texto("borrar"))).hasSize(1);

        indexador.eliminar(t.getId());
        indexador.eliminar(t.getId());   // idempotente
        assertThat(titulos(texto("borrar"))).isEmpty();

        indexador.indexar(t);
        indexador.recrearIndice();
        assertThat(titulos(texto("borrar"))).isEmpty();
    }

    @Test
    void siOpenSearchNoResponderSeLanzaBusquedaNoDisponible() {
        var caido = new OpenSearchBuscador(new OpenSearchCliente("http://127.0.0.1:1", Duration.ofMillis(500)), tickets, indice);
        assertThatThrownBy(() -> caido.buscar(texto("x"), 0, 20)).isInstanceOf(BusquedaNoDisponibleException.class);
    }
}
