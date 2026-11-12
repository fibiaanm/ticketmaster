package com.tickets.adapter.out.persistence;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "prompt_template")
class PromptTemplateEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;
    @Column(nullable = false, length = 100)
    String nombre;
    @Column(nullable = false)
    int version;
    @Column(name = "system_prompt", nullable = false, columnDefinition = "TEXT")
    String systemPrompt;
    @Column(name = "user_template", nullable = false, columnDefinition = "TEXT")
    String userTemplate;
    @Column(nullable = false)
    boolean activo;
    @Column(nullable = false, length = 64)
    String hash;
    @Column(name = "fecha_creacion", nullable = false)
    Instant fechaCreacion;
}
