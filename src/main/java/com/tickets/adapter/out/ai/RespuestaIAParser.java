package com.tickets.adapter.out.ai;

import com.tickets.application.port.out.AnalisisTicketResponse;
import com.tickets.domain.Categoria;
import com.tickets.domain.EnumParser;
import com.tickets.domain.Prioridad;
import com.tickets.domain.exception.RespuestaIAInvalidaException;
import com.tickets.domain.exception.SolicitudInvalidaException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Valida y normaliza el JSON devuelto por el modelo. La salida del LLM nunca es de confianza. */
public class RespuestaIAParser {

    static final int MAX_PALABRAS_RESUMEN = 30;
    static final int MAX_ETIQUETAS = 5;

    private final JsonMapper mapper = JsonMapper.builder().build();

    public AnalisisTicketResponse parsear(String contenido) {
        if (contenido == null || contenido.isBlank()) {
            throw new RespuestaIAInvalidaException("El modelo devolvió una respuesta vacía");
        }
        JsonNode json;
        try {
            json = mapper.readTree(quitarBloqueDeCodigo(contenido));
        } catch (RuntimeException e) {
            throw new RespuestaIAInvalidaException("El modelo no devolvió un JSON válido", e);
        }
        if (json == null || !json.isObject()) {
            throw new RespuestaIAInvalidaException("El modelo no devolvió un objeto JSON");
        }

        try {
            Categoria categoria = EnumParser.parsear(Categoria.class, texto(json, "categoria"), "Categoría inválida");
            Prioridad prioridad = EnumParser.parsear(Prioridad.class, texto(json, "prioridad"), "Prioridad inválida");
            String resumen = texto(json, "resumen");
            if (resumen == null || resumen.isBlank()) {
                throw new RespuestaIAInvalidaException("El modelo no devolvió resumen");
            }
            return new AnalisisTicketResponse(categoria.name(), prioridad.name(), recortar(resumen),
                    etiquetas(json.path("etiquetas")));
        } catch (SolicitudInvalidaException e) {
            throw new RespuestaIAInvalidaException("Respuesta del modelo inválida: " + e.getMessage(), e);
        }
    }

    private static String texto(JsonNode json, String campo) {
        JsonNode n = json.path(campo);
        return n.isString() ? n.asString() : null;
    }

    private static List<String> etiquetas(JsonNode nodo) {
        if (!nodo.isArray()) {
            throw new RespuestaIAInvalidaException("El modelo no devolvió etiquetas");
        }
        Set<String> etiquetas = new LinkedHashSet<>();
        for (JsonNode e : nodo) {
            if (e.isString() && !e.asString().isBlank() && etiquetas.size() < MAX_ETIQUETAS) {
                etiquetas.add(e.asString().strip().toLowerCase());
            }
        }
        if (etiquetas.isEmpty()) {
            throw new RespuestaIAInvalidaException("El modelo no devolvió etiquetas válidas");
        }
        return new ArrayList<>(etiquetas);
    }

    private static String recortar(String resumen) {
        String[] palabras = resumen.strip().split("\\s+");
        return String.join(" ", Arrays.copyOf(palabras, Math.min(palabras.length, MAX_PALABRAS_RESUMEN)));
    }

    private static String quitarBloqueDeCodigo(String s) {
        String t = s.strip();
        if (t.startsWith("```")) {
            t = t.replaceFirst("^```[a-zA-Z]*\\s*", "").replaceFirst("\\s*```$", "");
        }
        return t;
    }
}
