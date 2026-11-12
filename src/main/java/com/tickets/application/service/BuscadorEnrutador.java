package com.tickets.application.service;

import com.tickets.application.Pagina;
import com.tickets.application.port.out.BuscadorTickets;
import com.tickets.application.port.out.FiltroTickets;
import com.tickets.domain.Ticket;
import com.tickets.shared.log.Log;

import java.util.Map;

/**
 * Decide que motor resuelve cada listado.
 * <ul>
 *   <li>MYSQL: siempre MySQL.</li>
 *   <li>HIBRIDO: con texto (q) va a OpenSearch (relevancia, tildes, espanol); sin texto va a MySQL
 *       (consistencia fuerte). Es el modo recomendado hoy.</li>
 *   <li>OPENSEARCH: todo va a OpenSearch. Es el paso de migracion cuando los datos escalen: el adaptador ya
 *       soporta todos los filtros y el orden.</li>
 * </ul>
 * Si OpenSearch falla, se responde desde MySQL: la busqueda nunca deja de funcionar.
 */
public class BuscadorEnrutador implements BuscadorTickets {

    public enum Modo { MYSQL, HIBRIDO, OPENSEARCH }

    private final BuscadorTickets mysql;
    private final BuscadorTickets opensearch;
    private final Modo modo;

    public BuscadorEnrutador(BuscadorTickets mysql, BuscadorTickets opensearch, Modo modo) {
        this.mysql = mysql;
        this.opensearch = opensearch;
        this.modo = modo;
    }

    @Override
    public Pagina<Ticket> buscar(FiltroTickets filtro, int pagina, int tamano) {
        if (!usaOpenSearch(filtro)) {
            return mysql.buscar(filtro, pagina, tamano);
        }
        try {
            return opensearch.buscar(filtro, pagina, tamano);
        } catch (RuntimeException e) {
            Log.warn("OpenSearch no disponible; el listado se resuelve en MySQL",
                    Map.of("modo", modo.name(), "causa", e.getClass().getSimpleName()));
            return mysql.buscar(filtro, pagina, tamano);
        }
    }

    private boolean usaOpenSearch(FiltroTickets filtro) {
        return switch (modo) {
            case MYSQL -> false;
            case HIBRIDO -> filtro.tieneTexto();
            case OPENSEARCH -> true;
        };
    }
}
