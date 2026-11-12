package com.tickets.adapter.out.persistence;

import com.tickets.domain.*;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.List;

@Entity
@Table(name = "ticket")
class TicketEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;
    @Version
    Long version;
    @Column(nullable = false, length = 100)
    String titulo;
    @Column(nullable = false, columnDefinition = "TEXT")
    String descripcion;
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    Categoria categoria;
    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    Prioridad prioridad;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    EstadoTicket estado;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_solicitante_id", nullable = false)
    UsuarioEntity solicitante;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tecnico_asignado_id")
    UsuarioEntity tecnicoAsignado;
    @Column(name = "resumen_ia", length = 500)
    String resumenIA;
    @Convert(converter = EtiquetasConverter.class)
    @Column(name = "etiquetas_ia", length = 300)
    List<String> etiquetasIA;
    @Enumerated(EnumType.STRING)
    @Column(name = "estado_analisis", nullable = false, length = 15)
    EstadoAnalisis estadoAnalisis;
    @Enumerated(EnumType.STRING)
    @Column(name = "analizado_por", length = 10)
    OrigenAnalisis analizadoPor;
    @Column(name = "modelo_ia", length = 100)
    String modeloIA;
    @Column(name = "prompt_template_id")
    Long promptTemplateId;
    @Column(name = "fecha_creacion", nullable = false)
    Instant fechaCreacion;
    @Column(name = "fecha_actualizacion", nullable = false)
    Instant fechaActualizacion;
    @Column(name = "eliminado_en")
    Instant eliminadoEn;
}
