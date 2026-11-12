package com.tickets.adapter.out.ai;

import com.sun.net.httpserver.HttpServer;
import com.tickets.domain.exception.ConexionIAException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenAIProviderTest {

    HttpServer server;
    int status = 200;
    String cuerpoRespuesta = "";
    AtomicReference<String> path = new AtomicReference<>();
    AtomicReference<String> auth = new AtomicReference<>();
    AtomicReference<String> cuerpoPeticion = new AtomicReference<>();

    @BeforeEach
    void arrancar() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", ex -> {
            path.set(ex.getRequestURI().getPath());
            auth.set(ex.getRequestHeaders().getFirst("Authorization"));
            cuerpoPeticion.set(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] out = cuerpoRespuesta.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(status, out.length);
            ex.getResponseBody().write(out);
            ex.close();
        });
        server.start();
    }

    @AfterEach
    void detener() {
        server.stop(0);
    }

    OpenAIProvider provider(String apiKey) {
        String base = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
        return new OpenAIProvider(new AIProperties("openai", "gpt-test", apiKey, base, 5, 0, 0L));
    }

    @Test
    void enviaLaPeticionYDevuelveElContenido() {
        cuerpoRespuesta = "{\"model\":\"gpt-test-2026\",\"choices\":[{\"message\":{\"content\":\"{\\\"a\\\":1}\"}}]}";

        var r = provider("sk-secreta").generar("SYSTEM", "USER");

        assertThat(r.contenido()).isEqualTo("{\"a\":1}");
        assertThat(r.modelo()).isEqualTo("gpt-test-2026");
        assertThat(path.get()).isEqualTo("/v1/chat/completions");
        assertThat(auth.get()).isEqualTo("Bearer sk-secreta");

        JsonNode body = JsonMapper.builder().build().readTree(cuerpoPeticion.get());
        assertThat(body.path("model").asString()).isEqualTo("gpt-test");
        assertThat(body.path("messages").get(0).path("role").asString()).isEqualTo("system");
        assertThat(body.path("messages").get(0).path("content").asString()).isEqualTo("SYSTEM");
        assertThat(body.path("messages").get(1).path("content").asString()).isEqualTo("USER");
        assertThat(body.path("response_format").path("type").asString()).isEqualTo("json_object");
    }

    @Test
    void errorDelServidorEsReintentable() {
        status = 503;
        cuerpoRespuesta = "{}";
        assertThatThrownBy(() -> provider("k").generar("s", "u"))
                .isInstanceOfSatisfying(ConexionIAException.class, e -> assertThat(e.esReintentable()).isTrue());
    }

    @Test
    void errorDeCredencialesNoEsReintentable() {
        status = 401;
        cuerpoRespuesta = "{}";
        assertThatThrownBy(() -> provider("k").generar("s", "u"))
                .isInstanceOfSatisfying(ConexionIAException.class, e -> assertThat(e.esReintentable()).isFalse());
    }

    @Test
    void sinApiKeyFallaSinLlamarAlServidor() {
        assertThatThrownBy(() -> provider("").generar("s", "u"))
                .isInstanceOfSatisfying(ConexionIAException.class, e -> assertThat(e.esReintentable()).isFalse());
        assertThat(path.get()).isNull();
    }

    @Test
    void servidorInalcanzableEsReintentable() {
        server.stop(0);
        assertThatThrownBy(() -> provider("k").generar("s", "u"))
                .isInstanceOfSatisfying(ConexionIAException.class, e -> assertThat(e.esReintentable()).isTrue());
    }
}
