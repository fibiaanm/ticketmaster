package com.tickets.adapter.out.search;

import com.tickets.application.port.out.IndexadorBusqueda;
import com.tickets.domain.Ticket;

import java.util.concurrent.atomic.AtomicBoolean;

public class OpenSearchIndexador implements IndexadorBusqueda {

    private final OpenSearchCliente cliente;
    private final String indice;
    private final AtomicBoolean indiceListo = new AtomicBoolean(false);

    public OpenSearchIndexador(OpenSearchCliente cliente, String indice) {
        this.cliente = cliente;
        this.indice = indice;
    }

    /** Crea el indice si no existe. Idempotente; se invoca al arrancar y antes de la primera escritura. */
    public void asegurarIndice() {
        if (indiceListo.get()) {
            return;
        }
        // GET /{indice}: 200 con el indice como clave si existe; 404 con un "error" si no
        boolean existe = cliente.enviar("GET", "/" + indice, null, 404).has(indice);
        if (!existe) {
            cliente.enviar("PUT", "/" + indice, DocumentoTicket.MAPEO);
        }
        indiceListo.set(true);
    }

    @Override
    public void indexar(Ticket ticket) {
        asegurarIndice();
        cliente.enviar("PUT", "/%s/_doc/%d".formatted(indice, ticket.getId()), DocumentoTicket.desde(ticket));
    }

    @Override
    public void eliminar(Long ticketId) {
        asegurarIndice();
        cliente.enviar("DELETE", "/%s/_doc/%d".formatted(indice, ticketId), null, 404);
    }

    @Override
    public void recrearIndice() {
        cliente.enviar("DELETE", "/" + indice, null, 404);
        indiceListo.set(false);
        cliente.enviar("PUT", "/" + indice, DocumentoTicket.MAPEO);
        indiceListo.set(true);
    }
}
