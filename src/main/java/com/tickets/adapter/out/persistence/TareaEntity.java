package com.tickets.adapter.out.persistence;

import com.tickets.application.port.out.TipoTarea;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "tarea")
class TareaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    TipoTarea tipo;
    @Column(name = "ticket_id", nullable = false)
    Long ticketId;
    @Column(nullable = false, length = 15)
    String estado;
    @Column(nullable = false)
    int intentos;
    @Column(name = "ultimo_error", length = 500)
    String ultimoError;
    @Column(name = "request_id", length = 64)
    String requestId;
    @Column(name = "disponible_desde", nullable = false)
    Instant disponibleDesde;
    @Column(name = "fecha_creacion", nullable = false)
    Instant fechaCreacion;
    @Column(name = "fecha_actualizacion", nullable = false)
    Instant fechaActualizacion;
}
