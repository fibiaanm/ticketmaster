package com.tickets.config;

import com.tickets.adapter.out.persistence.MysqlBuscador;
import com.tickets.adapter.out.search.OpenSearchBuscador;
import com.tickets.adapter.out.search.OpenSearchCliente;
import com.tickets.adapter.out.search.OpenSearchIndexador;
import com.tickets.application.port.out.BuscadorTickets;
import com.tickets.application.port.out.ColaTareas;
import com.tickets.application.port.out.IndexadorBusqueda;
import com.tickets.application.port.out.TicketRepository;
import com.tickets.application.service.BuscadorEnrutador;
import com.tickets.application.service.IndexarTicketService;
import com.tickets.application.service.ReindexarService;
import com.tickets.shared.log.Log;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.time.Duration;
import java.util.Map;

/**
 * Listado y busqueda segun SEARCH_ENGINE:
 * <ul>
 *   <li>mysql: todo en MySQL (sin OpenSearch).</li>
 *   <li>hybrid: texto (q) en OpenSearch, resto en MySQL. Recomendado.</li>
 *   <li>opensearch: todo en OpenSearch (migracion futura); si cae, MySQL responde.</li>
 * </ul>
 */
@Configuration
public class SearchConfig {

    private static final String HABILITADO = "'${search.engine:mysql}' != 'mysql'";

    @Bean
    @Qualifier("mysql")
    BuscadorTickets buscadorMysql(TicketRepository tickets) {
        return new MysqlBuscador(tickets);
    }

    @Bean
    @ConditionalOnExpression(HABILITADO)
    OpenSearchCliente openSearchCliente(@Value("${search.opensearch.url:http://localhost:9200}") String url,
                                        @Value("${search.opensearch.timeout:3s}") Duration timeout) {
        return new OpenSearchCliente(url, timeout);
    }

    @Bean
    @ConditionalOnExpression(HABILITADO)
    OpenSearchIndexador openSearchIndexador(OpenSearchCliente cliente,
                                            @Value("${search.opensearch.index:tickets}") String indice) {
        return new OpenSearchIndexador(cliente, indice);
    }

    @Bean
    @ConditionalOnExpression(HABILITADO)
    @Qualifier("opensearch")
    BuscadorTickets buscadorOpenSearch(OpenSearchCliente cliente, TicketRepository tickets,
                                       @Value("${search.opensearch.index:tickets}") String indice) {
        return new OpenSearchBuscador(cliente, tickets, indice);
    }

    @Bean
    @ConditionalOnExpression(HABILITADO)
    IndexarTicketService indexarTicketService(TicketRepository tickets, IndexadorBusqueda indexador) {
        return new IndexarTicketService(tickets, indexador);
    }

    /** Si OpenSearch no esta listo al arrancar no se aborta: el indice se crea en la primera escritura. */
    @Bean
    @ConditionalOnExpression(HABILITADO)
    CommandLineRunner prepararIndice(OpenSearchIndexador indexador) {
        return args -> {
            try {
                indexador.asegurarIndice();
            } catch (RuntimeException e) {
                Log.warn("OpenSearch no disponible al arrancar; el indice se creara en la primera escritura",
                        Map.of("causa", e.getClass().getSimpleName()));
            }
        };
    }

    @Bean
    ReindexarService reindexarService(TicketRepository tickets, ColaTareas cola,
                                      @Value("${search.engine:mysql}") String motor,
                                      org.springframework.beans.factory.ObjectProvider<IndexadorBusqueda> indexador) {
        return new ReindexarService(tickets, cola, "mysql".equalsIgnoreCase(motor) ? null : indexador.getIfAvailable());
    }

    @Bean
    @Primary
    BuscadorTickets buscadorTickets(@Qualifier("mysql") BuscadorTickets mysql,
                                    @Qualifier("opensearch") org.springframework.beans.factory.ObjectProvider<BuscadorTickets> opensearch,
                                    @Value("${search.engine:mysql}") String motor) {
        BuscadorEnrutador.Modo modo = switch (motor.toLowerCase()) {
            case "hybrid", "hibrido" -> BuscadorEnrutador.Modo.HIBRIDO;
            case "opensearch" -> BuscadorEnrutador.Modo.OPENSEARCH;
            default -> BuscadorEnrutador.Modo.MYSQL;
        };
        return new BuscadorEnrutador(mysql, opensearch.getIfAvailable(() -> mysql), modo);
    }
}
