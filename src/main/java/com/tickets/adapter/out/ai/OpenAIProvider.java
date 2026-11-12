package com.tickets.adapter.out.ai;

import com.tickets.domain.exception.ConexionIAException;
import com.tickets.domain.exception.RespuestaIAInvalidaException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** Proveedor OpenAI (Chat Completions con salida JSON). */
public class OpenAIProvider implements AIModelProvider {

    private final AIProperties props;
    private final HttpClient http;
    private final JsonMapper mapper = JsonMapper.builder().build();

    public OpenAIProvider(AIProperties props) {
        this.props = props;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(props.timeoutSeconds())).build();
    }

    @Override
    public String nombre() {
        return "openai";
    }

    @Override
    public AIRespuesta generar(String systemPrompt, String instrucciones) {
        if (props.apiKey() == null || props.apiKey().isBlank()) {
            throw new ConexionIAException("OPENAI_API_KEY no está configurada", false, null);
        }

        ObjectNode body = mapper.createObjectNode();
        body.put("model", props.model());
        var mensajes = body.putArray("messages");
        mensajes.addObject().put("role", "system").put("content", systemPrompt);
        mensajes.addObject().put("role", "user").put("content", instrucciones);
        body.putObject("response_format").put("type", "json_object");

        HttpRequest request = HttpRequest.newBuilder(URI.create(props.baseUrl() + "/chat/completions"))
                .timeout(Duration.ofSeconds(props.timeoutSeconds()))
                .header("Authorization", "Bearer " + props.apiKey())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                .build();

        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new ConexionIAException("No se pudo conectar con OpenAI: " + e.getClass().getSimpleName(), true, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ConexionIAException("Llamada a OpenAI interrumpida", false, e);
        }

        int status = response.statusCode();
        if (status == 429 || status >= 500) {
            throw new ConexionIAException("OpenAI respondió " + status, true, null);
        }
        if (status >= 400) {
            throw new ConexionIAException("OpenAI rechazó la solicitud (" + status + ")", false, null);
        }

        try {
            JsonNode json = mapper.readTree(response.body());
            String contenido = json.path("choices").path(0).path("message").path("content").asString(null);
            if (contenido == null) {
                throw new RespuestaIAInvalidaException("OpenAI no devolvió contenido");
            }
            return new AIRespuesta(contenido, json.path("model").asString(props.model()));
        } catch (RespuestaIAInvalidaException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new RespuestaIAInvalidaException("Respuesta de OpenAI ilegible", e);
        }
    }
}
