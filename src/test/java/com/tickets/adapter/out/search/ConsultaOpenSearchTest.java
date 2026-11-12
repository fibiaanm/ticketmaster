package com.tickets.adapter.out.search;

import com.tickets.application.port.out.FiltroTickets;
import com.tickets.application.port.out.Orden;
import com.tickets.domain.Categoria;
import com.tickets.domain.EstadoTicket;
import com.tickets.domain.Prioridad;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ConsultaOpenSearchTest {

    JsonNode consulta(FiltroTickets f, int pagina, int tamano) {
        return OpenSearchCliente.JSON.readTree(ConsultaOpenSearch.construir(f, pagina, tamano).toString());
    }

    @Test
    void sinTextoNiFiltrosPideTodoOrdenadoPorLoMasReciente() {
        JsonNode q = consulta(new FiltroTickets(null, null, null, null), 2, 10);
        assertThat(q.path("from").asInt()).isEqualTo(20);
        assertThat(q.path("size").asInt()).isEqualTo(10);
        assertThat(q.path("_source").asBoolean(true)).isFalse();
        assertThat(q.path("query").path("bool").path("must").get(0).has("match_all")).isTrue();
        assertThat(q.path("query").path("bool").path("filter")).isEmpty();
        assertThat(q.path("sort").get(0).path("fechaCreacion").asString()).isEqualTo("desc");
        assertThat(q.path("sort").get(1).path("id").asString()).isEqualTo("desc");
    }

    @Test
    void elTextoBuscaEnTituloEtiquetasResumenYDescripcionConTolerancia() {
        JsonNode q = consulta(new FiltroTickets(null, null, null, null, null, null, " internet ", null), 0, 20);
        JsonNode multi = q.path("query").path("bool").path("must").get(0).path("multi_match");
        assertThat(multi.path("query").asString()).isEqualTo("internet");
        assertThat(multi.path("fields").toString()).contains("titulo^3", "etiquetasIA^2", "resumenIA", "descripcion");
        assertThat(multi.path("fuzziness").asString()).isEqualTo("AUTO");
    }

    @Test
    void conTextoYSinOrdenExplicitoOrdenaPorRelevancia() {
        JsonNode q = consulta(new FiltroTickets(null, null, null, null, null, null, "internet", null), 0, 20);
        assertThat(q.path("sort").get(0).path("_score").asString()).isEqualTo("desc");
    }

    @Test
    void conTextoYOrdenExplicitoGanaElOrdenDelUsuario() {
        JsonNode q = consulta(new FiltroTickets(null, null, null, null, null, null, "internet",
                Orden.parsear("titulo,asc")), 0, 20);
        assertThat(q.path("sort").get(0).path("titulo.keyword").asString()).isEqualTo("asc");
        assertThat(q.path("sort").toString()).doesNotContain("_score");
    }

    @Test
    void traduceTodosLosFiltrosIncluidoElAlcanceDelUsuarioYElRangoDeFechas() {
        var f = new FiltroTickets(EstadoTicket.EN_PROCESO, Categoria.HARDWARE, Prioridad.ALTA, 7L,
                Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-02-01T00:00:00Z"), null, null);
        JsonNode filtros = consulta(f, 0, 20).path("query").path("bool").path("filter");
        String s = filtros.toString();
        assertThat(s).contains("\"estado\":\"EN_PROCESO\"").contains("\"categoria\":\"HARDWARE\"")
                .contains("\"prioridad\":\"ALTA\"").contains("\"solicitanteId\":7")
                .contains("\"gte\":\"2026-01-01T00:00:00Z\"").contains("\"lte\":\"2026-02-01T00:00:00Z\"");
    }

    @Test
    void laPrioridadSeOrdenaPorGravedadNumericaYElIdNoAgregaDesempateDuplicado() {
        assertThat(consulta(new FiltroTickets(null, null, null, null, null, null, null, Orden.parsear("prioridad,desc")), 0, 20)
                .path("sort").get(0).path("prioridadOrden").asString()).isEqualTo("desc");
        JsonNode porId = consulta(new FiltroTickets(null, null, null, null, null, null, null, Orden.parsear("id,asc")), 0, 20);
        assertThat(porId.path("sort")).hasSize(1);
    }

    @Test
    void laGravedadCoincideConLaEscalaDeMysql() {
        assertThat(DocumentoTicket.gravedad(null)).isZero();
        assertThat(DocumentoTicket.gravedad(Prioridad.BAJA)).isEqualTo(1);
        assertThat(DocumentoTicket.gravedad(Prioridad.MEDIA)).isEqualTo(2);
        assertThat(DocumentoTicket.gravedad(Prioridad.ALTA)).isEqualTo(3);
        assertThat(DocumentoTicket.gravedad(Prioridad.CRITICA)).isEqualTo(4);
    }
}
