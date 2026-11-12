package com.tickets.support;

import com.tickets.application.Pagina;
import com.tickets.application.port.out.RegistroEventos;
import com.tickets.domain.EventoTicket;
import com.tickets.domain.TipoEvento;

import java.util.ArrayList;
import java.util.List;

public class InMemoryRegistroEventos implements RegistroEventos {

    public final List<EventoTicket> eventos = new ArrayList<>();

    @Override
    public void registrar(EventoTicket e) {
        eventos.add(new EventoTicket((long) eventos.size() + 1, e.ticketId(), e.tipo(), e.actor(), e.requestId(),
                e.detalle(), e.fecha()));
    }

    @Override
    public List<EventoTicket> deTicket(Long ticketId, List<TipoEvento> tipos) {
        return eventos.stream().filter(e -> e.ticketId().equals(ticketId))
                .filter(e -> tipos.isEmpty() || tipos.contains(e.tipo())).toList();
    }

    @Override
    public Pagina<EventoTicket> auditoria(FiltroAuditoria f, int pagina, int tamano) {
        List<EventoTicket> todos = eventos.stream()
                .filter(e -> f.ticketId() == null || e.ticketId().equals(f.ticketId()))
                .filter(e -> f.tipo() == null || e.tipo() == f.tipo())
                .filter(e -> f.actorId() == null || (e.actor() != null && e.actor().id().equals(f.actorId())))
                .filter(e -> f.desde() == null || !e.fecha().isBefore(f.desde()))
                .filter(e -> f.hasta() == null || !e.fecha().isAfter(f.hasta())).toList();
        int desde = Math.min(pagina * tamano, todos.size());
        return new Pagina<>(todos.subList(desde, Math.min(desde + tamano, todos.size())), pagina, tamano, todos.size());
    }
}
