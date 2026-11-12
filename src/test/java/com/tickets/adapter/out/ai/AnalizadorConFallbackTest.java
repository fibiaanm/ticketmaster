package com.tickets.adapter.out.ai;

import com.tickets.application.port.out.AnalisisTicketResponse;
import com.tickets.application.port.out.AnalizadorTicketService;
import com.tickets.domain.OrigenAnalisis;
import com.tickets.domain.exception.ConexionIAException;
import com.tickets.domain.exception.RespuestaIAInvalidaException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AnalizadorConFallbackTest {

    AnalizadorTicketService respaldo = (t, d) ->
            new AnalisisTicketResponse("OTRO", "MEDIA", "r", java.util.List.of("x"), OrigenAnalisis.REGLAS, "reglas-v1", null);

    @Test
    void usaElPrincipalCuandoFunciona() {
        AnalizadorTicketService principal = (t, d) ->
                new AnalisisTicketResponse("HARDWARE", "ALTA", "p", java.util.List.of("y"), OrigenAnalisis.LLM, "m", 1L);
        assertThat(new AnalizadorConFallback(principal, respaldo).analizar("t", "d").origen()).isEqualTo(OrigenAnalisis.LLM);
    }

    @Test
    void usaReglasSiFallaLaConexion() {
        AnalizadorTicketService principal = (t, d) -> { throw new ConexionIAException("caído", true, null); };
        assertThat(new AnalizadorConFallback(principal, respaldo).analizar("t", "d").origen()).isEqualTo(OrigenAnalisis.REGLAS);
    }

    @Test
    void usaReglasSiLaRespuestaEsInvalida() {
        AnalizadorTicketService principal = (t, d) -> { throw new RespuestaIAInvalidaException("basura"); };
        assertThat(new AnalizadorConFallback(principal, respaldo).analizar("t", "d").origen()).isEqualTo(OrigenAnalisis.REGLAS);
    }

    @Test
    void usaReglasTambienAnteCualquierErrorInesperado() {
        AnalizadorTicketService principal = (t, d) -> { throw new IllegalStateException("bug"); };
        assertThat(new AnalizadorConFallback(principal, respaldo).analizar("t", "d").origen()).isEqualTo(OrigenAnalisis.REGLAS);
    }
}
