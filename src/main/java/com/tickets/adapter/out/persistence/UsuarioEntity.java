package com.tickets.adapter.out.persistence;

import com.tickets.domain.Rol;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "usuario")
class UsuarioEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;
    @Column(nullable = false, length = 100)
    String nombre;
    @Column(nullable = false, length = 150, unique = true)
    String correo;
    @Column(nullable = false, length = 100)
    String password;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    Rol rol;
    @Column(name = "fecha_creacion", nullable = false)
    Instant fechaCreacion;

    // Getter (no campo): permite leer el id de un proxy lazy sin inicializarlo.
    Long getId() {
        return id;
    }
}
