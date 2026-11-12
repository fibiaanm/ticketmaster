package com.tickets.adapter.out.persistence;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "sesion")
class SesionEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;
    @Column(name = "token_hash", nullable = false, length = 64, unique = true)
    String tokenHash;
    @Column(name = "usuario_id", nullable = false)
    Long usuarioId;
    @Column(name = "expira_en", nullable = false)
    Instant expiraEn;
    @Column(nullable = false)
    boolean revocada;
    @Column(name = "fecha_creacion", nullable = false)
    Instant fechaCreacion;
}
