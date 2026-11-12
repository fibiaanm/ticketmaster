package com.tickets.domain;

import com.tickets.domain.exception.SolicitudInvalidaException;
import com.tickets.domain.exception.TecnicoInvalidoException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Agregado Ticket. Dominio puro: sin dependencias de Spring/JPA. */
public class Ticket {

    private Long id;
    private Long version;
    private String titulo;
    private String descripcion;
    private Categoria categoria;
    private Prioridad prioridad;
    private EstadoTicket estado;
    private Long solicitanteId;
    private Long tecnicoAsignadoId;
    private String resumenIA;
    private List<String> etiquetasIA = new ArrayList<>();
    private EstadoAnalisis estadoAnalisis;
    private OrigenAnalisis analizadoPor;
    private String modeloIA;
    private Long promptTemplateId;
    private Instant fechaCreacion;
    private Instant fechaActualizacion;
    private Instant eliminadoEn;

    private Ticket() {
    }

    public static Ticket nuevo(String titulo, String descripcion, Long solicitanteId, Instant ahora) {
        Ticket t = new Ticket();
        t.titulo = titulo;
        t.descripcion = descripcion;
        t.solicitanteId = solicitanteId;
        t.estado = EstadoTicket.ABIERTO;
        t.estadoAnalisis = EstadoAnalisis.PENDIENTE;
        t.fechaCreacion = ahora;
        t.fechaActualizacion = ahora;
        return t;
    }

    /** Reconstruccion desde persistencia. */
    public static Ticket reconstruir(Long id, Long version, String titulo, String descripcion, Categoria categoria,
                                     Prioridad prioridad, EstadoTicket estado, Long solicitanteId,
                                     Long tecnicoAsignadoId, String resumenIA, List<String> etiquetasIA,
                                     EstadoAnalisis estadoAnalisis, OrigenAnalisis analizadoPor, String modeloIA,
                                     Long promptTemplateId, Instant fechaCreacion, Instant fechaActualizacion,
                                     Instant eliminadoEn) {
        Ticket t = new Ticket();
        t.id = id;
        t.version = version;
        t.titulo = titulo;
        t.descripcion = descripcion;
        t.categoria = categoria;
        t.prioridad = prioridad;
        t.estado = estado;
        t.solicitanteId = solicitanteId;
        t.tecnicoAsignadoId = tecnicoAsignadoId;
        t.resumenIA = resumenIA;
        t.etiquetasIA = etiquetasIA == null ? new ArrayList<>() : new ArrayList<>(etiquetasIA);
        t.estadoAnalisis = estadoAnalisis;
        t.analizadoPor = analizadoPor;
        t.modeloIA = modeloIA;
        t.promptTemplateId = promptTemplateId;
        t.fechaCreacion = fechaCreacion;
        t.fechaActualizacion = fechaActualizacion;
        t.eliminadoEn = eliminadoEn;
        return t;
    }

    public void cambiarEstado(EstadoTicket nuevo, Instant ahora) {
        if (!estado.puedePasarA(nuevo)) {
            throw new SolicitudInvalidaException("No se puede cambiar el estado de %s a %s".formatted(estado, nuevo));
        }
        estado = nuevo;
        fechaActualizacion = ahora;
    }

    public void asignarTecnico(Usuario tecnico, Instant ahora) {
        if (tecnico.rol() != Rol.SOPORTE) {
            throw new TecnicoInvalidoException(tecnico.id());
        }
        tecnicoAsignadoId = tecnico.id();
        fechaActualizacion = ahora;
    }

    public void iniciarAnalisis(Instant ahora) {
        estadoAnalisis = EstadoAnalisis.ANALIZANDO;
        fechaActualizacion = ahora;
    }

    public void completarAnalisis(Categoria categoria, Prioridad prioridad, String resumen, List<String> etiquetas,
                                  OrigenAnalisis origen, String modelo, Long promptTemplateId, Instant ahora) {
        this.categoria = categoria;
        this.prioridad = prioridad;
        this.resumenIA = resumen;
        this.etiquetasIA = new ArrayList<>(etiquetas);
        this.analizadoPor = origen;
        this.modeloIA = modelo;
        this.promptTemplateId = promptTemplateId;
        this.estadoAnalisis = EstadoAnalisis.COMPLETADO;
        this.fechaActualizacion = ahora;
    }

    /** Un analisis en curso (PENDIENTE o ANALIZANDO) no se puede volver a solicitar. */
    public void solicitarReanalisis(Instant ahora) {
        if (estadoAnalisis == EstadoAnalisis.PENDIENTE || estadoAnalisis == EstadoAnalisis.ANALIZANDO) {
            throw new SolicitudInvalidaException("El ticket ya tiene un análisis en curso (%s)".formatted(estadoAnalisis));
        }
        estadoAnalisis = EstadoAnalisis.PENDIENTE;
        fechaActualizacion = ahora;
    }

    public void fallarAnalisis(Instant ahora) {
        estadoAnalisis = EstadoAnalisis.FALLIDO;
        fechaActualizacion = ahora;
    }

    public void eliminar(Instant ahora) {
        eliminadoEn = ahora;
        fechaActualizacion = ahora;
    }

    public boolean estaEliminado() {
        return eliminadoEn != null;
    }

    public boolean esVisiblePara(Actor actor) {
        return actor.rol() != Rol.USUARIO || solicitanteId.equals(actor.id());
    }

    public Long getId() { return id; }
    public Long getVersion() { return version; }
    public String getTitulo() { return titulo; }
    public String getDescripcion() { return descripcion; }
    public Categoria getCategoria() { return categoria; }
    public Prioridad getPrioridad() { return prioridad; }
    public EstadoTicket getEstado() { return estado; }
    public Long getSolicitanteId() { return solicitanteId; }
    public Long getTecnicoAsignadoId() { return tecnicoAsignadoId; }
    public String getResumenIA() { return resumenIA; }
    public List<String> getEtiquetasIA() { return List.copyOf(etiquetasIA); }
    public EstadoAnalisis getEstadoAnalisis() { return estadoAnalisis; }
    public OrigenAnalisis getAnalizadoPor() { return analizadoPor; }
    public String getModeloIA() { return modeloIA; }
    public Long getPromptTemplateId() { return promptTemplateId; }
    public Instant getFechaCreacion() { return fechaCreacion; }
    public Instant getFechaActualizacion() { return fechaActualizacion; }
    public Instant getEliminadoEn() { return eliminadoEn; }
}
