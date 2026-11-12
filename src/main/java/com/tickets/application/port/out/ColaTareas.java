package com.tickets.application.port.out;

import java.time.Instant;
import java.util.Optional;

/** Cola de tareas en segundo plano (analisis de IA, indexacion). Hoy en MySQL; reemplazable por un broker. */
public interface ColaTareas {
    void encolar(TipoTarea tipo, Long ticketId, String requestId);

    /** Reclama la siguiente tarea disponible (incrementa intentos). Seguro con varios workers. */
    Optional<Tarea> tomarSiguiente();

    void completar(Long tareaId);

    void reprogramar(Long tareaId, String error, Instant disponibleDesde);

    void marcarFallida(Long tareaId, String error);
}
