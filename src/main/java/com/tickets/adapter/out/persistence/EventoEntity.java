package com.tickets.adapter.out.persistence;

import com.tickets.domain.Rol;
import com.tickets.domain.TipoEvento;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "ticket_evento")
class EventoEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;
    @Column(name = "ticket_id", nullable = false)
    Long ticketId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    TipoEvento tipo;
    @Column(name = "actor_id")
    Long actorId;
    @Enumerated(EnumType.STRING)
    @Column(name = "actor_rol", length = 20)
    Rol actorRol;
    @Column(name = "request_id", length = 64)
    String requestId;
    @Column(columnDefinition = "TEXT")
    String detalle;
    @Column(nullable = false)
    Instant fecha;
}
