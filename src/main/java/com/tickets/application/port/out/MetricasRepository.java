package com.tickets.application.port.out;

public interface MetricasRepository {
    /** Metricas de los tickets no eliminados. */
    MetricasTickets calcular();
}
