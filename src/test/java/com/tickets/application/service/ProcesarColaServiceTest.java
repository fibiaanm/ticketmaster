package com.tickets.application.service;

import com.tickets.application.port.in.ManejadorTarea;
import com.tickets.application.port.out.TipoTarea;
import com.tickets.support.InMemoryColaTareas;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ProcesarColaServiceTest {

    Clock reloj = Clock.fixed(Instant.parse("2026-03-01T10:00:00Z"), ZoneOffset.UTC);
    InMemoryColaTareas cola = new InMemoryColaTareas();
    List<String> manejadas = new ArrayList<>();
    boolean falla = false;

    ProcesarColaService servicio(int maxIntentos) {
        ManejadorTarea analizar = id -> { if (falla) throw new IllegalStateException("fallo"); manejadas.add("A" + id); };
        ManejadorTarea indexar = id -> manejadas.add("I" + id);
        return new ProcesarColaService(cola, Map.of(TipoTarea.ANALIZAR, analizar, TipoTarea.INDEXAR, indexar),
                reloj, maxIntentos, Duration.ofSeconds(30));
    }

    @Test
    void colaVaciaNoHaceNada() {
        assertThat(servicio(3).procesarSiguiente()).isFalse();
    }

    @Test
    void despachaCadaTareaAlManejadorDeSuTipo() {
        cola.encolar(TipoTarea.ANALIZAR, 10L, "req-1");
        cola.encolar(TipoTarea.INDEXAR, 11L, "req-2");
        var s = servicio(3);
        assertThat(s.procesarSiguiente()).isTrue();
        assertThat(s.procesarSiguiente()).isTrue();
        assertThat(manejadas).containsExactly("A10", "I11");
        assertThat(cola.items).allMatch(i -> i.estado.equals("COMPLETADO"));
    }

    @Test
    void reprogramaConEsperaSiFallaYHayIntentosRestantes() {
        cola.encolar(TipoTarea.ANALIZAR, 10L, "req-1");
        falla = true;
        servicio(3).procesarSiguiente();

        var item = cola.items.get(0);
        assertThat(item.estado).isEqualTo("PENDIENTE");
        assertThat(item.error).contains("fallo");
        assertThat(item.disponibleDesde).isEqualTo(reloj.instant().plusSeconds(30));
    }

    @Test
    void marcaFallidaAlAgotarLosIntentos() {
        cola.encolar(TipoTarea.ANALIZAR, 10L, "req-1");
        falla = true;
        var s = servicio(2);
        s.procesarSiguiente();
        s.procesarSiguiente();
        assertThat(cola.items.get(0).estado).isEqualTo("FALLIDO");
        assertThat(s.procesarSiguiente()).isFalse();
    }

    @Test
    void unTipoSinManejadorFallaSinRomperElWorker() {
        cola.encolar(TipoTarea.INDEXAR, 5L, null);
        var s = new ProcesarColaService(cola, Map.of(), reloj, 1, Duration.ofSeconds(1));
        assertThat(s.procesarSiguiente()).isTrue();
        assertThat(cola.items.get(0).estado).isEqualTo("FALLIDO");
    }
}
