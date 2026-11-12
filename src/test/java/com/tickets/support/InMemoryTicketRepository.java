package com.tickets.support;

import com.tickets.application.Pagina;
import com.tickets.application.port.out.FiltroTickets;
import com.tickets.application.port.out.TicketRepository;
import com.tickets.domain.Ticket;
import com.tickets.domain.exception.ConflictoConcurrenciaException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Repositorio en memoria con copias y bloqueo optimista, como el real. */
public class InMemoryTicketRepository implements TicketRepository {

    public final Map<Long, Ticket> items = new LinkedHashMap<>();
    private long seq = 0;

    @Override
    public Ticket guardar(Ticket t) {
        long version = 0;
        Long id = t.getId();
        if (id == null) {
            id = ++seq;
        } else {
            Ticket actual = items.get(id);
            if (actual != null) {
                if (!actual.getVersion().equals(t.getVersion())) {
                    throw new ConflictoConcurrenciaException("version obsoleta del ticket " + id);
                }
                version = actual.getVersion() + 1;
            }
        }
        Ticket guardado = copia(t, id, version);
        items.put(id, guardado);
        return copia(guardado, id, version);
    }

    @Override
    public Optional<Ticket> buscarPorId(Long id) {
        return Optional.ofNullable(items.get(id)).filter(t -> !t.estaEliminado())
                .map(t -> copia(t, t.getId(), t.getVersion()));
    }

    @Override
    public Pagina<Ticket> buscar(FiltroTickets f, int pagina, int tamano) {
        List<Ticket> todos = items.values().stream().filter(t -> !t.estaEliminado())
                .filter(t -> f.estado() == null || t.getEstado() == f.estado())
                .filter(t -> f.categoria() == null || t.getCategoria() == f.categoria())
                .filter(t -> f.prioridad() == null || t.getPrioridad() == f.prioridad())
                .filter(t -> f.usuarioSolicitanteId() == null || t.getSolicitanteId().equals(f.usuarioSolicitanteId()))
                .toList();
        int desde = Math.min(pagina * tamano, todos.size());
        int hasta = Math.min(desde + tamano, todos.size());
        return new Pagina<>(todos.subList(desde, hasta), pagina, tamano, todos.size());
    }

    @Override
    public List<Ticket> buscarPorIds(List<Long> ids) {
        return ids.stream().map(this::buscarPorId).flatMap(Optional::stream).toList();
    }

    private static Ticket copia(Ticket t, Long id, long version) {
        return Ticket.reconstruir(id, version, t.getTitulo(), t.getDescripcion(), t.getCategoria(), t.getPrioridad(),
                t.getEstado(), t.getSolicitanteId(), t.getTecnicoAsignadoId(), t.getResumenIA(), t.getEtiquetasIA(),
                t.getEstadoAnalisis(), t.getAnalizadoPor(), t.getModeloIA(), t.getPromptTemplateId(),
                t.getFechaCreacion(), t.getFechaActualizacion(), t.getEliminadoEn());
    }
}
