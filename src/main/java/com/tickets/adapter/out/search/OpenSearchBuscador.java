package com.tickets.adapter.out.search;

import com.tickets.application.Pagina;
import com.tickets.application.port.out.BuscadorTickets;
import com.tickets.application.port.out.FiltroTickets;
import com.tickets.application.port.out.TicketRepository;
import com.tickets.domain.Ticket;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Listado y busqueda en OpenSearch. El indice solo decide QUE tickets y en que ORDEN; los datos que se
 * devuelven se leen de MySQL por id (siempre frescos, y los eliminados desaparecen aunque el indice vaya atrasado).
 */
public class OpenSearchBuscador implements BuscadorTickets {

    private final OpenSearchCliente cliente;
    private final TicketRepository tickets;
    private final String indice;

    public OpenSearchBuscador(OpenSearchCliente cliente, TicketRepository tickets, String indice) {
        this.cliente = cliente;
        this.tickets = tickets;
        this.indice = indice;
    }

    @Override
    public Pagina<Ticket> buscar(FiltroTickets filtro, int pagina, int tamano) {
        JsonNode respuesta = cliente.enviar("POST", "/" + indice + "/_search",
                ConsultaOpenSearch.construir(filtro, pagina, tamano).toString());

        List<Long> ids = new ArrayList<>();
        respuesta.path("hits").path("hits").forEach(h -> ids.add(Long.valueOf(h.path("_id").asString())));
        long total = respuesta.path("hits").path("total").path("value").asLong(ids.size());
        return new Pagina<>(tickets.buscarPorIds(ids), pagina, tamano, total);
    }
}
