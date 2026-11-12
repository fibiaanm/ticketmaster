package com.tickets.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class EstadoTicketTest {

    @ParameterizedTest
    @CsvSource({
            "ABIERTO,EN_PROCESO,true",
            "ABIERTO,CERRADO,true",
            "ABIERTO,RESUELTO,false",
            "EN_PROCESO,RESUELTO,true",
            "EN_PROCESO,ABIERTO,true",
            "RESUELTO,CERRADO,true",
            "RESUELTO,EN_PROCESO,true",
            "CERRADO,ABIERTO,false",
            "CERRADO,EN_PROCESO,false",
            "ABIERTO,ABIERTO,false"
    })
    void transicionesPermitidas(EstadoTicket desde, EstadoTicket hacia, boolean permitida) {
        assertThat(desde.puedePasarA(hacia)).isEqualTo(permitida);
    }
}
