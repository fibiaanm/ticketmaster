package com.tickets.adapter.out.persistence;

import com.tickets.application.port.out.ColaTareas;
import com.tickets.application.port.out.Tarea;
import com.tickets.application.port.out.TipoTarea;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/** Cola respaldada en MySQL (SELECT ... FOR UPDATE SKIP LOCKED). Reemplazable por un broker. */
@Component
@Transactional
class JpaColaTareas implements ColaTareas {

    static final Duration TIEMPO_VISIBILIDAD = Duration.ofMinutes(10);

    private final TareaJpaRepository jpa;
    private final Clock reloj;

    JpaColaTareas(TareaJpaRepository jpa, Clock reloj) {
        this.jpa = jpa;
        this.reloj = reloj;
    }

    @Override
    public void encolar(TipoTarea tipo, Long ticketId, String requestId) {
        Instant ahora = reloj.instant();
        TareaEntity e = new TareaEntity();
        e.tipo = tipo;
        e.ticketId = ticketId;
        e.estado = "PENDIENTE";
        e.intentos = 0;
        e.requestId = requestId;
        e.disponibleDesde = ahora;
        e.fechaCreacion = ahora;
        e.fechaActualizacion = ahora;
        jpa.save(e);
    }

    @Override
    public Optional<Tarea> tomarSiguiente() {
        Instant ahora = reloj.instant();
        return jpa.reclamarSiguiente(ahora, ahora.minus(TIEMPO_VISIBILIDAD)).map(e -> {
            e.estado = "PROCESANDO";
            e.intentos++;
            e.fechaActualizacion = ahora;
            return new Tarea(e.id, e.tipo, e.ticketId, e.intentos, e.requestId);
        });
    }

    @Override
    public void completar(Long tareaId) {
        actualizar(tareaId, "COMPLETADO", null, null);
    }

    @Override
    public void reprogramar(Long tareaId, String error, Instant disponibleDesde) {
        actualizar(tareaId, "PENDIENTE", error, disponibleDesde);
    }

    @Override
    public void marcarFallida(Long tareaId, String error) {
        actualizar(tareaId, "FALLIDO", error, null);
    }

    private void actualizar(Long tareaId, String estado, String error, Instant disponibleDesde) {
        TareaEntity e = jpa.findById(tareaId).orElseThrow();
        e.estado = estado;
        e.ultimoError = error == null ? null : error.substring(0, Math.min(error.length(), 500));
        if (disponibleDesde != null) {
            e.disponibleDesde = disponibleDesde;
        }
        e.fechaActualizacion = reloj.instant();
    }
}
