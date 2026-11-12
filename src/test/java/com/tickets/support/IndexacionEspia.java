package com.tickets.support;

import com.tickets.application.port.out.ProgramadorIndexacion;

import java.util.ArrayList;
import java.util.List;

public class IndexacionEspia implements ProgramadorIndexacion {
    public final List<Long> programados = new ArrayList<>();

    @Override
    public void programar(Long ticketId) { programados.add(ticketId); }
}
