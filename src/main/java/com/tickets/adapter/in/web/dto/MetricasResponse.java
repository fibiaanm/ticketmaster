package com.tickets.adapter.in.web.dto;

import com.tickets.application.port.out.MetricasTickets;

import java.util.List;
import java.util.Map;

public record MetricasResponse(long total, Map<String, Long> porCategoria, Map<String, Long> porPrioridad,
                               Map<String, Long> porEstado, Map<String, Long> porEstadoAnalisis,
                               List<MetricasTickets.Celda> categoriaPorPrioridad) {
    public static MetricasResponse desde(MetricasTickets m) {
        return new MetricasResponse(m.total(), m.porCategoria(), m.porPrioridad(), m.porEstado(),
                m.porEstadoAnalisis(), m.categoriaPorPrioridad());
    }
}
