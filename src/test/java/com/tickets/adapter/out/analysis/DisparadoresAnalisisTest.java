package com.tickets.adapter.out.analysis;

import com.tickets.domain.Ticket;
import com.tickets.shared.log.Contexto;
import com.tickets.support.InMemoryColaTareas;
import com.tickets.support.InMemoryTicketRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DisparadoresAnalisisTest {

    Ticket ticket = new InMemoryTicketRepository()
            .guardar(Ticket.nuevo("Titulo largo", "Descripcion suficientemente larga", 1L, Instant.EPOCH));

    @AfterEach
    void limpiar() {
        Contexto.limpiar();
    }

    @Test
    void syncAnalizaDespuesDelCommit() {
        List<Long> analizados = new ArrayList<>();
        var sync = new SyncDisparadorAnalisis(analizados::add);
        sync.registrar(ticket);
        assertThat(analizados).isEmpty();
        sync.ejecutarTrasCommit(ticket);
        assertThat(analizados).containsExactly(ticket.getId());
    }

    @Test
    void asyncEncolaConElRequestIdActual() {
        InMemoryColaTareas cola = new InMemoryColaTareas();
        Contexto.requestId("req-42");
        var async = new AsyncDisparadorAnalisis(cola);
        async.registrar(ticket);
        async.ejecutarTrasCommit(ticket);

        assertThat(cola.items).hasSize(1);
        assertThat(cola.items.get(0).tipo).isEqualTo(com.tickets.application.port.out.TipoTarea.ANALIZAR);
        assertThat(cola.items.get(0).ticketId).isEqualTo(ticket.getId());
        assertThat(cola.items.get(0).requestId).isEqualTo("req-42");
    }
}
