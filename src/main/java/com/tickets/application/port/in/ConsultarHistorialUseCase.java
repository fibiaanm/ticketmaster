package com.tickets.application.port.in;

import com.tickets.application.Pagina;
import com.tickets.application.port.out.RegistroEventos.FiltroAuditoria;
import com.tickets.domain.Actor;
import com.tickets.domain.EventoTicket;

import java.util.List;

public interface ConsultarHistorialUseCase {
    /** Historial de cambios de estado de un ticket (visible para quien puede ver el ticket). */
    List<EventoTicket> historialDeEstados(Actor actor, Long ticketId);

    /** Linea de tiempo completa de un ticket (SOPORTE, ADMIN). */
    List<EventoTicket> lineaDeTiempo(Actor actor, Long ticketId);

    /** Auditoria global (ADMIN). */
    Pagina<EventoTicket> auditoria(Actor actor, FiltroAuditoria filtro, int pagina, int tamano);
}
