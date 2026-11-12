package com.tickets.application.port.out;

import java.util.List;
import java.util.Map;

public record MetricasTickets(long total, Map<String, Long> porCategoria, Map<String, Long> porPrioridad,
                              Map<String, Long> porEstado, Map<String, Long> porEstadoAnalisis,
                              List<Celda> categoriaPorPrioridad) {

    /** Etiqueta de los tickets aun sin categoria/prioridad (analisis pendiente o fallido). */
    public static final String SIN_ANALIZAR = "SIN_ANALIZAR";

    public record Celda(String categoria, String prioridad, long total) {
    }
}
