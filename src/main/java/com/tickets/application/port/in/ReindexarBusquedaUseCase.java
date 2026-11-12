package com.tickets.application.port.in;

import com.tickets.domain.Actor;

public interface ReindexarBusquedaUseCase {
    /** @return cantidad de tickets programados para reindexar. */
    int reindexar(Actor actor, boolean recrearIndice);
}
