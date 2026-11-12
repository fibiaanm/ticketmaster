package com.tickets.adapter.out.ai;

import com.tickets.domain.exception.ConexionIAException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TicketAIAnalysisTest {

    static AIProperties props(String provider, int retries) {
        return new AIProperties(provider, "m", "k", "http://x", 5, retries, 0L);
    }

    static AIModelProvider proveedor(String nombre, java.util.function.Supplier<AIModelProvider.AIRespuesta> accion,
                                     AtomicInteger llamadas) {
        return new AIModelProvider() {
            public String nombre() { return nombre; }
            public AIRespuesta generar(String s, String u) { llamadas.incrementAndGet(); return accion.get(); }
        };
    }

    @Test
    void delegaAlProveedorConfigurado() {
        AtomicInteger a = new AtomicInteger(), b = new AtomicInteger();
        var facade = new TicketAIAnalysis(List.of(
                proveedor("openai", () -> new AIModelProvider.AIRespuesta("A", "ma"), a),
                proveedor("otro", () -> new AIModelProvider.AIRespuesta("B", "mb"), b)), props("otro", 1));

        assertThat(facade.analyze("sys", "usr").contenido()).isEqualTo("B");
        assertThat(a.get()).isZero();
        assertThat(b.get()).isEqualTo(1);
    }

    @Test
    void proveedorDesconocidoFallaClaramente() {
        var facade = new TicketAIAnalysis(List.of(), props("inexistente", 1));
        assertThatThrownBy(() -> facade.analyze("s", "u"))
                .isInstanceOf(ConexionIAException.class).hasMessageContaining("inexistente");
    }

    @Test
    void reintentaErroresTransitorios() {
        AtomicInteger llamadas = new AtomicInteger();
        var facade = new TicketAIAnalysis(List.of(proveedor("openai", () -> {
            if (llamadas.get() < 2) throw new ConexionIAException("timeout", true, null);
            return new AIModelProvider.AIRespuesta("ok", "m");
        }, llamadas)), props("openai", 2));

        assertThat(facade.analyze("s", "u").contenido()).isEqualTo("ok");
        assertThat(llamadas.get()).isEqualTo(2 + 1 - 1 + 0).isEqualTo(2);
    }

    @Test
    void noReintentaErroresNoTransitorios() {
        AtomicInteger llamadas = new AtomicInteger();
        var facade = new TicketAIAnalysis(List.of(proveedor("openai",
                () -> { throw new ConexionIAException("401", false, null); }, llamadas)), props("openai", 3));

        assertThatThrownBy(() -> facade.analyze("s", "u")).isInstanceOf(ConexionIAException.class);
        assertThat(llamadas.get()).isEqualTo(1);
    }

    @Test
    void agotaReintentosYPropagaElError() {
        AtomicInteger llamadas = new AtomicInteger();
        var facade = new TicketAIAnalysis(List.of(proveedor("openai",
                () -> { throw new ConexionIAException("503", true, null); }, llamadas)), props("openai", 2));

        assertThatThrownBy(() -> facade.analyze("s", "u")).isInstanceOf(ConexionIAException.class);
        assertThat(llamadas.get()).isEqualTo(3); // intento inicial + 2 reintentos
    }
}
