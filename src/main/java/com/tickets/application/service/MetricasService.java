package com.tickets.application.service;

import com.tickets.application.port.in.ConsultarMetricasUseCase;
import com.tickets.application.port.out.MetricasRepository;
import com.tickets.application.port.out.MetricasTickets;
import com.tickets.domain.Actor;
import com.tickets.domain.Rol;
import com.tickets.domain.exception.AccesoDenegadoException;

public class MetricasService implements ConsultarMetricasUseCase {

    private final MetricasRepository metricas;

    public MetricasService(MetricasRepository metricas) {
        this.metricas = metricas;
    }

    @Override
    public MetricasTickets obtener(Actor actor) {
        if (actor.rol() == Rol.USUARIO) {
            throw new AccesoDenegadoException("Su rol no permite consultar las métricas");
        }
        return metricas.calcular();
    }
}
