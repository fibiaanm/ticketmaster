package com.tickets.adapter.out.persistence;

import com.tickets.application.Pagina;
import com.tickets.application.port.out.BuscadorTickets;
import com.tickets.application.port.out.FiltroTickets;
import com.tickets.application.port.out.TicketRepository;
import com.tickets.domain.Ticket;

/** Motor de listado respaldado por MySQL (fuente de la verdad). */
public class MysqlBuscador implements BuscadorTickets {

    private final TicketRepository tickets;

    public MysqlBuscador(TicketRepository tickets) {
        this.tickets = tickets;
    }

    @Override
    public Pagina<Ticket> buscar(FiltroTickets filtro, int pagina, int tamano) {
        return tickets.buscar(filtro, pagina, tamano);
    }
}
