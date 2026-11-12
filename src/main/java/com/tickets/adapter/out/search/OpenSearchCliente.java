package com.tickets.adapter.out.search;

import com.tickets.domain.exception.BusquedaNoDisponibleException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** Cliente HTTP minimo de OpenSearch (REST). Cualquier fallo se reporta como BusquedaNoDisponibleException. */
public class OpenSearchCliente {

    static final JsonMapper JSON = JsonMapper.builder().build();

    private final String urlBase;
    private final Duration timeout;
    private final HttpClient http;

    public OpenSearchCliente(String urlBase, Duration timeout) {
        this.urlBase = urlBase.endsWith("/") ? urlBase.substring(0, urlBase.length() - 1) : urlBase;
        this.timeout = timeout;
        this.http = HttpClient.newBuilder().connectTimeout(timeout).build();
    }

    public JsonNode enviar(String metodo, String ruta, String cuerpoJson, int... statusAceptados) {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(urlBase + ruta)).timeout(timeout)
                .header("Content-Type", "application/json");
        b.method(metodo, cuerpoJson == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(cuerpoJson));
        try {
            HttpResponse<String> r = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
            int status = r.statusCode();
            boolean ok = status >= 200 && status < 300;
            for (int aceptado : statusAceptados) {
                ok = ok || status == aceptado;
            }
            if (!ok) {
                throw new BusquedaNoDisponibleException(
                        "OpenSearch respondió %d en %s %s".formatted(status, metodo, ruta), null);
            }
            return r.body() == null || r.body().isBlank() ? JSON.createObjectNode() : JSON.readTree(r.body());
        } catch (IOException e) {
            throw new BusquedaNoDisponibleException("No se pudo contactar con OpenSearch", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusquedaNoDisponibleException("Llamada a OpenSearch interrumpida", e);
        }
    }
}
