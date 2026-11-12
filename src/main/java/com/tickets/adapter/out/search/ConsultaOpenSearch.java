package com.tickets.adapter.out.search;

import com.tickets.application.port.out.FiltroTickets;
import com.tickets.application.port.out.Orden;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import static com.tickets.adapter.out.search.OpenSearchCliente.JSON;

/**
 * Traduce los criterios del listado a una consulta de OpenSearch. Soporta TODOS los filtros y el orden,
 * no solo el texto: asi migrar a "solo OpenSearch" es cambiar SEARCH_ENGINE.
 */
final class ConsultaOpenSearch {

    private ConsultaOpenSearch() {
    }

    static ObjectNode construir(FiltroTickets f, int pagina, int tamano) {
        ObjectNode cuerpo = JSON.createObjectNode();
        cuerpo.put("from", pagina * tamano);
        cuerpo.put("size", tamano);
        cuerpo.put("track_total_hits", true);
        cuerpo.put("_source", false);   // solo ids: los datos frescos se leen de MySQL

        ObjectNode bool = cuerpo.putObject("query").putObject("bool");
        if (f.tieneTexto()) {
            ObjectNode multi = bool.putArray("must").addObject().putObject("multi_match");
            multi.put("query", f.texto().strip());
            multi.putArray("fields").add("titulo^3").add("etiquetasIA^2").add("resumenIA").add("descripcion");
            multi.put("fuzziness", "AUTO");
            multi.put("minimum_should_match", "70%");
        } else {
            bool.putArray("must").addObject().putObject("match_all");
        }

        ArrayNode filtros = bool.putArray("filter");
        if (f.estado() != null) termino(filtros, "estado", f.estado().name());
        if (f.categoria() != null) termino(filtros, "categoria", f.categoria().name());
        if (f.prioridad() != null) termino(filtros, "prioridad", f.prioridad().name());
        if (f.usuarioSolicitanteId() != null) {
            filtros.addObject().putObject("term").put("solicitanteId", f.usuarioSolicitanteId());
        }
        if (f.desde() != null || f.hasta() != null) {
            ObjectNode rango = filtros.addObject().putObject("range").putObject("fechaCreacion");
            if (f.desde() != null) rango.put("gte", f.desde().toString());
            if (f.hasta() != null) rango.put("lte", f.hasta().toString());
        }

        cuerpo.set("sort", ordenar(f));
        return cuerpo;
    }

    private static void termino(ArrayNode filtros, String campo, String valor) {
        filtros.addObject().putObject("term").put(campo, valor);
    }

    private static ArrayNode ordenar(FiltroTickets f) {
        ArrayNode sort = JSON.createArrayNode();
        Orden orden = f.orden();
        if (orden == null && f.tieneTexto()) {
            sort.addObject().put("_score", "desc");               // sin orden explicito: relevancia
            sort.addObject().put("fechaCreacion", "desc");
        } else {
            Orden efectivo = orden == null ? Orden.POR_DEFECTO : orden;
            sort.addObject().put(campoOrden(efectivo.campo()), efectivo.direccion().name().toLowerCase());
        }
        if (orden == null || orden.campo() != Orden.Campo.ID) {
            sort.addObject().put("id", "desc");                   // desempate estable para paginar
        }
        return sort;
    }

    private static String campoOrden(Orden.Campo campo) {
        return switch (campo) {
            case ID -> "id";
            case FECHA_CREACION -> "fechaCreacion";
            case FECHA_ACTUALIZACION -> "fechaActualizacion";
            case PRIORIDAD -> "prioridadOrden";   // gravedad numerica, igual que en MySQL
            case TITULO -> "titulo.keyword";      // normalizador sin mayusculas ni tildes, igual que la coleccion de MySQL
        };
    }
}
