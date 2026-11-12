package com.tickets.adapter.in.web;

import com.sun.net.httpserver.HttpServer;
import com.tickets.support.ApiTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Flujo completo con el proveedor OpenAI simulado por un servidor HTTP local (sin red externa). */
class AnalisisConLlmIT extends ApiTestBase {

    static HttpServer openai;
    static volatile int status = 200;
    static volatile String contenidoModelo;
    static final AtomicInteger llamadas = new AtomicInteger();

    @BeforeAll
    static void arrancar() throws IOException {
        openai = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        openai.createContext("/", ex -> {
            llamadas.incrementAndGet();
            ex.getRequestBody().readAllBytes();
            String cuerpo = "{\"model\":\"gpt-simulado\",\"choices\":[{\"message\":{\"content\":"
                    + tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(contenidoModelo) + "}}]}";
            byte[] out = (status == 200 ? cuerpo : "{}").getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(status, out.length);
            ex.getResponseBody().write(out);
            ex.close();
        });
        openai.start();
    }

    @AfterAll
    static void detener() {
        openai.stop(0);
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) throws IOException {
        // el servidor debe existir antes de crear el contexto
        if (openai == null) arrancar();
        r.add("ai.base-url", () -> "http://127.0.0.1:" + openai.getAddress().getPort() + "/v1");
        r.add("ai.api-key", () -> "sk-test");
    }

    @BeforeEach
    void reiniciar() {
        status = 200;
        llamadas.set(0);
        contenidoModelo = "{\"categoria\":\"HARDWARE\",\"prioridad\":\"CRITICA\",\"resumen\":\"Equipo sin encender\","
                + "\"etiquetas\":[\"equipo\",\"urgente\"]}";
    }

    String crear() throws Exception {
        return mvc.perform(jwt(post("/api/tickets"), usuario).contentType(MediaType.APPLICATION_JSON)
                        .content(ticketJson("Problema con internet", DESC_INTERNET, usuario.id())))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    }

    @Test
    void clasificaConElModeloYGuardaLaTrazabilidad() throws Exception {
        crearYVerificar();
    }

    private void crearYVerificar() throws Exception {
        mvc.perform(jwt(post("/api/tickets"), usuario).contentType(MediaType.APPLICATION_JSON)
                        .content(ticketJson("Problema con internet", DESC_INTERNET, usuario.id())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.categoria").value("HARDWARE"))
                .andExpect(jsonPath("$.prioridad").value("CRITICA"))
                .andExpect(jsonPath("$.resumenIA").value("Equipo sin encender"))
                .andExpect(jsonPath("$.etiquetasIA", contains("equipo", "urgente")));
        // la trazabilidad (origen, modelo y version del prompt) queda en la linea de tiempo del ticket
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM ticket_evento WHERE tipo = 'ANALISIS_COMPLETADO' "
                        + "AND detalle LIKE '%\"analizadoPor\":\"LLM\"%' AND detalle LIKE '%gpt-simulado%'", Integer.class)).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(llamadas.get()).isEqualTo(1);
        // trazabilidad: queda ligado a la version activa del prompt
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM ticket t JOIN prompt_template p ON p.id = t.prompt_template_id "
                        + "WHERE p.nombre = 'ticket-analysis' AND p.activo = TRUE", Integer.class)).isEqualTo(1);
    }

    @Test
    void siElModeloFallaUsaElClasificadorLocal() throws Exception {
        status = 500;
        mvc.perform(jwt(post("/api/tickets"), usuario).contentType(MediaType.APPLICATION_JSON)
                        .content(ticketJson("Problema con internet", DESC_INTERNET, usuario.id())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estadoAnalisis").value("COMPLETADO"))
                .andExpect(jsonPath("$.categoria").value("CONECTIVIDAD"));
        asegurarOrigenReglas();
    }

    @Test
    void siElModeloDevuelveBasuraUsaElClasificadorLocal() throws Exception {
        contenidoModelo = "lo siento, no puedo ayudarte con eso";
        mvc.perform(jwt(post("/api/tickets"), usuario).contentType(MediaType.APPLICATION_JSON)
                        .content(ticketJson("Problema con internet", DESC_INTERNET, usuario.id())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.categoria").value("CONECTIVIDAD"));
        asegurarOrigenReglas();
    }

    @Test
    void siElModeloDevuelveCategoriaInventadaUsaElClasificadorLocal() throws Exception {
        contenidoModelo = "{\"categoria\":\"MAGIA\",\"prioridad\":\"ALTA\",\"resumen\":\"x\",\"etiquetas\":[\"a\"]}";
        mvc.perform(jwt(post("/api/tickets"), usuario).contentType(MediaType.APPLICATION_JSON)
                        .content(ticketJson("Problema con internet", DESC_INTERNET, usuario.id())))
                .andExpect(jsonPath("$.estadoAnalisis").value("COMPLETADO"));
        asegurarOrigenReglas();
    }

    private void asegurarOrigenReglas() {
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM ticket_evento WHERE tipo = 'ANALISIS_COMPLETADO' "
                        + "AND detalle LIKE '%\"analizadoPor\":\"REGLAS\"%'", Integer.class)).isEqualTo(1);
    }
}
