package com.tickets.application.port.out;

import com.tickets.domain.Categoria;
import com.tickets.domain.EstadoTicket;
import com.tickets.domain.Prioridad;

import java.time.Instant;

/**
 * Criterios del listado. texto = busqueda por titulo/descripcion; orden null = el motor decide
 * (por relevancia si hay texto y el motor puntua, si no, el mas reciente primero).
 */
public record FiltroTickets(EstadoTicket estado, Categoria categoria, Prioridad prioridad,
                            Long usuarioSolicitanteId, Instant desde, Instant hasta, String texto, Orden orden) {

    public FiltroTickets(EstadoTicket estado, Categoria categoria, Prioridad prioridad, Long usuarioSolicitanteId) {
        this(estado, categoria, prioridad, usuarioSolicitanteId, null, null, null, null);
    }

    public FiltroTickets conSolicitante(Long id) {
        return new FiltroTickets(estado, categoria, prioridad, id, desde, hasta, texto, orden);
    }

    public boolean tieneTexto() {
        return texto != null && !texto.isBlank();
    }
}
