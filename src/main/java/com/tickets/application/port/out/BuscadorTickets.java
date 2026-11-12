package com.tickets.application.port.out;

import com.tickets.application.Pagina;
import com.tickets.domain.Ticket;

/** Motor de busqueda y listado. Hoy: MySQL y/o OpenSearch segun SEARCH_ENGINE. */
public interface BuscadorTickets {
    Pagina<Ticket> buscar(FiltroTickets filtro, int pagina, int tamano);
}
