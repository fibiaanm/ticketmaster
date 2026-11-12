package com.tickets.adapter.out.ai;

import com.tickets.application.port.out.AnalisisTicketResponse;
import com.tickets.domain.OrigenAnalisis;
import com.tickets.domain.PromptTemplate;
import com.tickets.domain.exception.ConexionIAException;
import com.tickets.support.InMemoryPromptRepository;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LlmAnalizadorTest {

    InMemoryPromptRepository prompts = new InMemoryPromptRepository();
    List<String[]> recibidos = new ArrayList<>();
    String respuesta = "{\"categoria\":\"CONECTIVIDAD\",\"prioridad\":\"ALTA\",\"resumen\":\"Sin internet\","
            + "\"etiquetas\":[\"internet\",\"reunión\"]}";

    LlmAnalizador analizador() {
        AIModelProvider proveedor = new AIModelProvider() {
            public String nombre() { return "openai"; }
            public AIRespuesta generar(String s, String u) {
                recibidos.add(new String[]{s, u});
                return new AIRespuesta(respuesta, "gpt-test-2026");
            }
        };
        var facade = new TicketAIAnalysis(List.of(proveedor), new AIProperties("openai", "m", "k", null, 5, 0, 0L));
        return new LlmAnalizador(facade, prompts, new RespuestaIAParser());
    }

    @Test
    void usaElPromptActivoYAgregaTrazabilidad() {
        PromptTemplate p = prompts.guardar(new PromptTemplate(null, "ticket-analysis", 3, "SYS", "T:{titulo}|D:{descripcion}", true, "h"));

        AnalisisTicketResponse r = analizador().analizar("Internet caído", "No tengo conexión");

        assertThat(recibidos).hasSize(1);
        assertThat(recibidos.get(0)[0]).isEqualTo("SYS");
        assertThat(recibidos.get(0)[1]).isEqualTo("T:Internet caído|D:No tengo conexión");
        assertThat(r.categoria()).isEqualTo("CONECTIVIDAD");
        assertThat(r.origen()).isEqualTo(OrigenAnalisis.LLM);
        assertThat(r.modelo()).isEqualTo("gpt-test-2026");
        assertThat(r.promptTemplateId()).isEqualTo(p.id());
    }

    @Test
    void sinPromptActivoFalla() {
        assertThatThrownBy(() -> analizador().analizar("t", "d")).isInstanceOf(ConexionIAException.class)
                .hasMessageContaining("prompt");
    }
}
