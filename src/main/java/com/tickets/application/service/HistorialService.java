package com.tickets.application.service;

import com.tickets.application.Pagina;
import com.tickets.application.port.in.ConsultarHistorialUseCase;
import com.tickets.application.port.out.RegistroEventos;
import com.tickets.application.port.out.RegistroEventos.FiltroAuditoria;
import com.tickets.application.port.out.TicketRepository;
import com.tickets.domain.Actor;
import com.tickets.domain.EventoTicket;
import com.tickets.domain.Rol;
import com.tickets.domain.Ticket;
import com.tickets.domain.TipoEvento;
import com.tickets.domain.exception.AccesoDenegadoException;
import com.tickets.domain.exception.RecursoNoEncontradoException;

import java.util.List;

public class HistorialService implements ConsultarHistorialUseCase {

    private final TicketRepository tickets;
    private final RegistroEventos eventos;

    public HistorialService(TicketRepository tickets, RegistroEventos eventos) {
        this.tickets = tickets;
        this.eventos = eventos;
    }

    @Override
    public List<EventoTicket> historialDeEstados(Actor actor, Long ticketId) {
        visible(actor, ticketId);
        return eventos.deTicket(ticketId, List.of(TipoEvento.ESTADO_CAMBIADO));
    }

    @Override
    public List<EventoTicket> lineaDeTiempo(Actor actor, Long ticketId) {
        if (actor.rol() == Rol.USUARIO) {
            throw new AccesoDenegadoException("Su rol no permite ver la línea de tiempo completa");
        }
        visible(actor, ticketId);
        return eventos.deTicket(ticketId, List.of());
    }

    @Override
    public Pagina<EventoTicket> auditoria(Actor actor, FiltroAuditoria filtro, int pagina, int tamano) {
        if (actor.rol() != Rol.ADMIN) {
            throw new AccesoDenegadoException("Solo un ADMIN puede consultar la auditoría");
        }
        return eventos.auditoria(filtro, pagina, tamano);
    }

    private Ticket visible(Actor actor, Long ticketId) {
        Ticket t = tickets.buscarPorId(ticketId).orElseThrow(() -> RecursoNoEncontradoException.ticket(ticketId));
        if (!t.esVisiblePara(actor)) {
            throw RecursoNoEncontradoException.ticket(ticketId);
        }
        return t;
    }
}
