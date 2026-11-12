package com.tickets.application.port.out;

import com.tickets.domain.Ticket;

/** Mantiene el indice de busqueda (OpenSearch) alineado con MySQL, que es la fuente de la verdad. */
public interface IndexadorBusqueda {
    void indexar(Ticket ticket);

    void eliminar(Long ticketId);

    /** Borra y recrea el indice vacio (para un reindexado completo). */
    void recrearIndice();
}
