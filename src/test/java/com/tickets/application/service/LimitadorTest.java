package com.tickets.application.service;

import com.tickets.adapter.out.cache.MemoriaContadorTasa;
import com.tickets.application.service.Limitador.Decision;
import com.tickets.support.RelojMutable;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class LimitadorTest {

    // 10:00:10 dentro de una ventana de 60 s que empezo a las 10:00:00
    RelojMutable reloj = new RelojMutable(Instant.parse("2026-01-01T10:00:10Z"));
    Limitador limitador = new Limitador(new MemoriaContadorTasa(reloj), reloj);
    Duration ventana = Duration.ofSeconds(60);

    @Test
    void permiteHastaElLimiteYBloqueaDespues() {
        for (int i = 1; i <= 3; i++) {
            Decision d = limitador.evaluar("login:ip1", 3, ventana);
            assertThat(d.permitido()).isTrue();
            assertThat(d.restantes()).isEqualTo(3 - i);
        }
        Decision bloqueada = limitador.evaluar("login:ip1", 3, ventana);
        assertThat(bloqueada.permitido()).isFalse();
        assertThat(bloqueada.restantes()).isZero();
        assertThat(bloqueada.reintentarEnSegundos()).isEqualTo(50); // faltan 50 s para la proxima ventana
    }

    @Test
    void cadaClaveTieneSuPropiaCuenta() {
        for (int i = 0; i < 3; i++) limitador.evaluar("login:ip1", 3, ventana);
        assertThat(limitador.evaluar("login:ip1", 3, ventana).permitido()).isFalse();
        assertThat(limitador.evaluar("login:ip2", 3, ventana).permitido()).isTrue();
    }

    @Test
    void alCambiarDeVentanaElContadorSeReinicia() {
        for (int i = 0; i < 4; i++) limitador.evaluar("k", 3, ventana);
        assertThat(limitador.evaluar("k", 3, ventana).permitido()).isFalse();

        reloj.avanzar(Duration.ofSeconds(51)); // 10:01:01 -> nueva ventana
        assertThat(limitador.evaluar("k", 3, ventana).permitido()).isTrue();
    }
}
