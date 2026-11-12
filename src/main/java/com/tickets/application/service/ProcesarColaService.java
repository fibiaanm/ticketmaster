package com.tickets.application.service;

import com.tickets.application.port.in.ManejadorTarea;
import com.tickets.application.port.in.ProcesarColaUseCase;
import com.tickets.application.port.out.ColaTareas;
import com.tickets.application.port.out.Tarea;
import com.tickets.application.port.out.TipoTarea;
import com.tickets.shared.log.Contexto;
import com.tickets.shared.log.Log;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

public class ProcesarColaService implements ProcesarColaUseCase {

    private final ColaTareas cola;
    private final Map<TipoTarea, ManejadorTarea> manejadores;
    private final Clock reloj;
    private final int maxIntentos;
    private final Duration espera;

    public ProcesarColaService(ColaTareas cola, Map<TipoTarea, ManejadorTarea> manejadores, Clock reloj,
                               int maxIntentos, Duration espera) {
        this.cola = cola;
        this.manejadores = manejadores;
        this.reloj = reloj;
        this.maxIntentos = maxIntentos;
        this.espera = espera;
    }

    @Override
    public boolean procesarSiguiente() {
        Optional<Tarea> siguiente = cola.tomarSiguiente();
        if (siguiente.isEmpty()) {
            return false;
        }
        Tarea tarea = siguiente.get();
        Contexto.requestId(tarea.requestId());
        try {
            ManejadorTarea manejador = manejadores.get(tarea.tipo());
            if (manejador == null) {
                throw new IllegalStateException("No hay manejador para tareas " + tarea.tipo());
            }
            manejador.manejar(tarea.ticketId());
            cola.completar(tarea.id());
        } catch (RuntimeException e) {
            String error = e.getClass().getSimpleName() + ": " + e.getMessage();
            if (tarea.intentos() >= maxIntentos) {
                cola.marcarFallida(tarea.id(), error);
                Log.critical("Tarea fallida definitivamente",
                        Map.of("tipo", tarea.tipo().name(), "ticketId", tarea.ticketId(),
                                "intentos", tarea.intentos(), "error", error), e);
            } else {
                cola.reprogramar(tarea.id(), error, reloj.instant().plus(espera));
                Log.warn("Tarea fallida; se reintentará", Map.of("tipo", tarea.tipo().name(),
                        "ticketId", tarea.ticketId(), "intento", tarea.intentos(), "error", error));
            }
        } finally {
            Contexto.limpiar();
        }
        return true;
    }
}
