package com.tickets.adapter.out.search;

import com.tickets.domain.Prioridad;
import com.tickets.domain.Ticket;
import tools.jackson.databind.node.ObjectNode;

import static com.tickets.adapter.out.search.OpenSearchCliente.JSON;

/** Documento del indice y su mapeo (analizador de espanol, sin tildes). */
final class DocumentoTicket {

    private DocumentoTicket() {
    }

    static String desde(Ticket t) {
        ObjectNode d = JSON.createObjectNode();
        d.put("id", t.getId());
        d.put("titulo", t.getTitulo());
        d.put("descripcion", t.getDescripcion());
        d.put("resumenIA", t.getResumenIA());
        var etiquetas = d.putArray("etiquetasIA");
        t.getEtiquetasIA().forEach(etiquetas::add);
        if (t.getCategoria() != null) d.put("categoria", t.getCategoria().name());
        if (t.getPrioridad() != null) d.put("prioridad", t.getPrioridad().name());
        d.put("prioridadOrden", gravedad(t.getPrioridad()));
        d.put("estado", t.getEstado().name());
        d.put("estadoAnalisis", t.getEstadoAnalisis().name());
        d.put("solicitanteId", t.getSolicitanteId());
        if (t.getTecnicoAsignadoId() != null) d.put("tecnicoId", t.getTecnicoAsignadoId());
        d.put("fechaCreacion", t.getFechaCreacion().toString());
        d.put("fechaActualizacion", t.getFechaActualizacion().toString());
        return JSON.writeValueAsString(d);
    }

    /** Misma escala que el ORDER BY de MySQL: BAJA=1 ... CRITICA=4; sin prioridad=0. */
    static int gravedad(Prioridad p) {
        return p == null ? 0 : p.ordinal() + 1;
    }

    static final String MAPEO = """
            {
              "settings": {
                "number_of_shards": 1,
                "number_of_replicas": 0,
                "analysis": {
                  "filter": {
                    "es_stop": {"type": "stop", "stopwords": "_spanish_"},
                    "es_stemmer": {"type": "stemmer", "language": "light_spanish"}
                  },
                  "analyzer": {
                    "es_texto": {"type": "custom", "tokenizer": "standard",
                                 "filter": ["lowercase", "asciifolding", "es_stop", "es_stemmer"]}
                  },
                  "normalizer": {
                    "ci": {"type": "custom", "filter": ["lowercase", "asciifolding"]}
                  }
                }
              },
              "mappings": {
                "properties": {
                  "id": {"type": "long"},
                  "titulo": {"type": "text", "analyzer": "es_texto",
                             "fields": {"keyword": {"type": "keyword", "normalizer": "ci", "ignore_above": 256}}},
                  "descripcion": {"type": "text", "analyzer": "es_texto"},
                  "resumenIA": {"type": "text", "analyzer": "es_texto"},
                  "etiquetasIA": {"type": "text", "analyzer": "es_texto"},
                  "categoria": {"type": "keyword"},
                  "prioridad": {"type": "keyword"},
                  "prioridadOrden": {"type": "integer"},
                  "estado": {"type": "keyword"},
                  "estadoAnalisis": {"type": "keyword"},
                  "solicitanteId": {"type": "long"},
                  "tecnicoId": {"type": "long"},
                  "fechaCreacion": {"type": "date"},
                  "fechaActualizacion": {"type": "date"}
                }
              }
            }
            """;
}
