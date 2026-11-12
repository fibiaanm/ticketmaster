package com.tickets.adapter.in.web;

import com.tickets.application.port.in.ProcesarColaUseCase;
import com.tickets.support.ApiTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** ANALYSIS_MODE=async: el POST responde con el analisis PENDIENTE y el worker lo completa. */
@TestPropertySource(properties = "analysis.mode=async")
class AnalisisAsyncIT extends ApiTestBase {

    @Autowired ProcesarColaUseCase worker;

    @Test
    void elTicketNaceSinAnalisisYElWorkerLoCompleta() throws Exception {
        var res = mvc.perform(jwt(post("/api/tickets"), usuario).contentType(MediaType.APPLICATION_JSON)
                        .content(ticketJson("Problema con internet", DESC_INTERNET, usuario.id())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estadoAnalisis").value("PENDIENTE"))
                .andExpect(jsonPath("$.categoria").value(nullValue()))
                .andExpect(jsonPath("$.etiquetasIA", empty()))
                .andReturn();
        long id = JSON.readTree(res.getResponse().getContentAsString()).path("id").asLong();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tarea WHERE tipo = 'ANALIZAR' AND estado = 'PENDIENTE'", Integer.class)).isEqualTo(1);

        assertThat(worker.procesarSiguiente()).isTrue();
        assertThat(worker.procesarSiguiente()).isFalse();

        mvc.perform(jwt(get("/api/tickets/" + id), usuario))
                .andExpect(jsonPath("$.estadoAnalisis").value("COMPLETADO"))
                .andExpect(jsonPath("$.categoria").value("CONECTIVIDAD"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tarea WHERE tipo = 'ANALIZAR' AND estado = 'COMPLETADO'", Integer.class)).isEqualTo(1);
    }

    @Test
    void elJobConservaElRequestIdDeLaPeticionOriginal() throws Exception {
        mvc.perform(jwt(post("/api/tickets"), usuario).header("X-Request-Id", "req-trazable-001")
                .contentType(MediaType.APPLICATION_JSON)
                .content(ticketJson("Problema con internet", DESC_INTERNET, usuario.id()))).andExpect(status().isCreated());
        assertThat(jdbc.queryForObject("SELECT request_id FROM tarea WHERE tipo = 'ANALIZAR'", String.class)).isEqualTo("req-trazable-001");
    }

    @Test
    void filtraPorEstadoDeAnalisisIndexado() throws Exception {
        mvc.perform(jwt(post("/api/tickets"), usuario).contentType(MediaType.APPLICATION_JSON)
                .content(ticketJson("Problema con internet", DESC_INTERNET, usuario.id()))).andExpect(status().isCreated());
        // el indice por estado_analisis existe para esta consulta (ver migracion V1)
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ticket WHERE estado_analisis = 'PENDIENTE'", Integer.class)).isEqualTo(1);
    }
}
