package com.tickets.domain;

import java.util.EnumSet;
import java.util.Set;

public enum EstadoTicket {
    ABIERTO, EN_PROCESO, RESUELTO, CERRADO;

    public boolean puedePasarA(EstadoTicket destino) {
        return permitidos().contains(destino);
    }

    private Set<EstadoTicket> permitidos() {
        return switch (this) {
            case ABIERTO -> EnumSet.of(EN_PROCESO, CERRADO);
            case EN_PROCESO -> EnumSet.of(RESUELTO, ABIERTO);
            case RESUELTO -> EnumSet.of(CERRADO, EN_PROCESO);
            case CERRADO -> EnumSet.noneOf(EstadoTicket.class);
        };
    }
}
