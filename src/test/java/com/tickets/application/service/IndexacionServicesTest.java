package com.tickets.application.service;

import com.tickets.application.port.out.ColaTareas;
import com.tickets.application.port.out.IndexadorBusqueda;
import com.tickets.application.port.out.TipoTarea;
import com.tickets.domain.Actor;
import com.tickets.domain.Rol;
import com.tickets.domain.Ticket;
import com.tickets.domain.exception.AccesoDenegadoException;
import com.tickets.domain.exception.SolicitudInvalidaException;
import com.tickets.support.InMemoryColaTareas;
import com.tickets.support.InMemoryTicketRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class IndexacionServicesTest {

    @Mock IndexadorBusqueda indexador;
    InMemoryTicketRepository tickets = new InMemoryTicketRepository();
    InMemoryColaTareas cola = new InMemoryColaTareas();

    Ticket nuevo() {
        return tickets.guardar(Ticket.nuevo("Titulo largo", "Descripcion suficientemente larga", 1L, Instant.EPOCH));
    }

    // ---- IndexarTicketService
    @Test
    void indexaElEstadoActualDelTicket() {
        Ticket t = nuevo();
        new IndexarTicketService(tickets, indexador).manejar(t.getId());
        verify(indexador).indexar(any(Ticket.class));
        verify(indexador, never()).eliminar(any());
    }

    @Test
    void siElTicketYaNoExisteOFueEliminadoSaleDelIndice() {
        Ticket t = nuevo();
        Ticket borrado = tickets.buscarPorId(t.getId()).orElseThrow();
        borrado.eliminar(Instant.EPOCH);
        tickets.guardar(borrado);

        var servicio = new IndexarTicketService(tickets, indexador);
        servicio.manejar(t.getId());
        servicio.manejar(999L);

        verify(indexador).eliminar(t.getId());
        verify(indexador).eliminar(999L);
        verify(indexador, never()).indexar(any());
    }

    // ---- ReindexarService
    @Test
    void reindexarEncolaUnaTareaPorTicketYPuedeRecrearElIndice() {
        nuevo(); nuevo(); nuevo();
        int n = new ReindexarService(tickets, cola, indexador).reindexar(new Actor(1L, Rol.ADMIN), true);

        assertThat(n).isEqualTo(3);
        assertThat(cola.items).hasSize(3).allMatch(i -> i.tipo == TipoTarea.INDEXAR);
        verify(indexador).recrearIndice();
    }

    @Test
    void reindexarPagina() {
        for (int i = 0; i < 450; i++) nuevo();   // mas de 2 paginas de 200
        assertThat(new ReindexarService(tickets, cola, indexador).reindexar(new Actor(1L, Rol.ADMIN), false)).isEqualTo(450);
    }

    @Test
    void soloAdminReindexa() {
        var servicio = new ReindexarService(tickets, cola, indexador);
        assertThatThrownBy(() -> servicio.reindexar(new Actor(2L, Rol.SOPORTE), false)).isInstanceOf(AccesoDenegadoException.class);
        verifyNoInteractions(indexador);
    }

    @Test
    void sinOpenSearchHabilitadoSeRechazaConUnMensajeClaro() {
        var servicio = new ReindexarService(tickets, cola, null);
        assertThatThrownBy(() -> servicio.reindexar(new Actor(1L, Rol.ADMIN), false))
                .isInstanceOf(SolicitudInvalidaException.class).hasMessageContaining("SEARCH_ENGINE=mysql");
    }
}
