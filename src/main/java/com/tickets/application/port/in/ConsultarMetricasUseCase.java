package com.tickets.application.port.in;

import com.tickets.application.port.out.MetricasTickets;
import com.tickets.domain.Actor;

public interface ConsultarMetricasUseCase {
    MetricasTickets obtener(Actor actor);
}
