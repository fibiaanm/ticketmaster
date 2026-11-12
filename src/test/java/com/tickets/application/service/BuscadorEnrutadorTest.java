package com.tickets.application.service;

import com.tickets.application.Pagina;
import com.tickets.application.port.out.BuscadorTickets;
import com.tickets.application.port.out.FiltroTickets;
import com.tickets.application.service.BuscadorEnrutador.Modo;
import com.tickets.domain.Ticket;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BuscadorEnrutadorTest {

    @Mock BuscadorTickets mysql;
    @Mock BuscadorTickets opensearch;

    final Pagina<Ticket> deMysql = new Pagina<>(List.of(), 0, 20, 1);
    final Pagina<Ticket> deOpenSearch = new Pagina<>(List.of(), 0, 20, 2);
    final FiltroTickets sinTexto = new FiltroTickets(null, null, null, null);
    final FiltroTickets conTexto = new FiltroTickets(null, null, null, null, null, null, "internet", null);

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.lenient().when(mysql.buscar(any(), anyInt(), anyInt())).thenReturn(deMysql);
        org.mockito.Mockito.lenient().when(opensearch.buscar(any(), anyInt(), anyInt())).thenReturn(deOpenSearch);
    }

    @Test
    void modoMysqlNuncaUsaOpenSearch() {
        var r = new BuscadorEnrutador(mysql, opensearch, Modo.MYSQL);
        assertThat(r.buscar(conTexto, 0, 20)).isSameAs(deMysql);
        verifyNoInteractions(opensearch);
    }

    @Test
    void modoHibridoUsaOpenSearchSoloConTexto() {
        var r = new BuscadorEnrutador(mysql, opensearch, Modo.HIBRIDO);
        assertThat(r.buscar(conTexto, 0, 20)).isSameAs(deOpenSearch);
        assertThat(r.buscar(sinTexto, 0, 20)).isSameAs(deMysql);
        verify(mysql).buscar(sinTexto, 0, 20);
        verify(opensearch, never()).buscar(sinTexto, 0, 20);
    }

    @Test
    void modoHibridoTratalaBusquedaEnBlancoComoSinTexto() {
        var r = new BuscadorEnrutador(mysql, opensearch, Modo.HIBRIDO);
        var enBlanco = new FiltroTickets(null, null, null, null, null, null, "   ", null);
        assertThat(r.buscar(enBlanco, 0, 20)).isSameAs(deMysql);
    }

    @Test
    void modoOpenSearchEnviaTodoAOpenSearch() {
        var r = new BuscadorEnrutador(mysql, opensearch, Modo.OPENSEARCH);
        assertThat(r.buscar(sinTexto, 0, 20)).isSameAs(deOpenSearch);
        verifyNoInteractions(mysql);
    }

    @Test
    void siOpenSearchFallaSeRespondeDesdeMysql() {
        when(opensearch.buscar(any(), anyInt(), anyInt())).thenThrow(new IllegalStateException("opensearch caído"));
        assertThat(new BuscadorEnrutador(mysql, opensearch, Modo.HIBRIDO).buscar(conTexto, 0, 20)).isSameAs(deMysql);
        assertThat(new BuscadorEnrutador(mysql, opensearch, Modo.OPENSEARCH).buscar(sinTexto, 0, 20)).isSameAs(deMysql);
    }
}
