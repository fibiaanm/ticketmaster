package com.tickets.adapter.out.analysis;

import com.tickets.application.port.out.ProgramadorIndexacion;

/** SEARCH_ENGINE=mysql: no hay indice externo que mantener. */
public class IndexacionDesactivada implements ProgramadorIndexacion {

    @Override
    public void programar(Long ticketId) {
        // nada: MySQL es el unico motor
    }
}
