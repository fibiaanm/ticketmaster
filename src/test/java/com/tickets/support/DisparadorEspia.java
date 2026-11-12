package com.tickets.support;

import com.tickets.application.port.out.DisparadorAnalisis;
import com.tickets.domain.Ticket;

import java.util.ArrayList;
import java.util.List;

public class DisparadorEspia implements DisparadorAnalisis {
    public final List<Long> registrados = new ArrayList<>();
    public final List<Long> ejecutados = new ArrayList<>();
    public boolean fallaAlEjecutar = false;

    @Override
    public void registrar(Ticket ticket) { registrados.add(ticket.getId()); }

    @Override
    public void ejecutarTrasCommit(Ticket ticket) {
        if (fallaAlEjecutar) throw new IllegalStateException("analisis caído");
        ejecutados.add(ticket.getId());
    }
}
