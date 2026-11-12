package com.tickets.adapter.out.ai;

import com.tickets.application.port.out.AnalisisTicketResponse;
import com.tickets.domain.exception.RespuestaIAInvalidaException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RespuestaIAParserTest {

    RespuestaIAParser parser = new RespuestaIAParser();

    @Test
    void parseaJsonValido() {
        AnalisisTicketResponse r = parser.parsear("""
                {"categoria":"CONECTIVIDAD","prioridad":"ALTA","resumen":"Sin internet antes de reunión",
                 "etiquetas":["Internet","conectividad"]}""");
        assertThat(r.categoria()).isEqualTo("CONECTIVIDAD");
        assertThat(r.prioridad()).isEqualTo("ALTA");
        assertThat(r.etiquetas()).containsExactly("internet", "conectividad");
    }

    @Test
    void aceptaJsonEnvueltoEnBloqueDeCodigo() {
        AnalisisTicketResponse r = parser.parsear("```json\n{\"categoria\":\"otro\",\"prioridad\":\"baja\","
                + "\"resumen\":\"x\",\"etiquetas\":[\"a\"]}\n```");
        assertThat(r.categoria()).isEqualTo("OTRO");
        assertThat(r.prioridad()).isEqualTo("BAJA");
    }

    @Test
    void recortaEtiquetasASincoYResumenA30Palabras() {
        String resumen = "palabra ".repeat(40).strip();
        AnalisisTicketResponse r = parser.parsear("{\"categoria\":\"OTRO\",\"prioridad\":\"BAJA\",\"resumen\":\""
                + resumen + "\",\"etiquetas\":[\"a\",\"b\",\"c\",\"d\",\"e\",\"f\",\"g\"]}");
        assertThat(r.etiquetas()).hasSize(5);
        assertThat(r.resumen().split("\\s+")).hasSize(30);
    }

    @Test
    void rechazaCategoriaOPrioridadInvalida() {
        assertThatThrownBy(() -> parser.parsear("{\"categoria\":\"MAGIA\",\"prioridad\":\"ALTA\",\"resumen\":\"x\","
                + "\"etiquetas\":[\"a\"]}")).isInstanceOf(RespuestaIAInvalidaException.class);
        assertThatThrownBy(() -> parser.parsear("{\"categoria\":\"OTRO\",\"prioridad\":\"URGENTISIMA\",\"resumen\":\"x\","
                + "\"etiquetas\":[\"a\"]}")).isInstanceOf(RespuestaIAInvalidaException.class);
    }

    @Test
    void rechazaJsonMalformadoOCamposFaltantes() {
        assertThatThrownBy(() -> parser.parsear("esto no es json")).isInstanceOf(RespuestaIAInvalidaException.class);
        assertThatThrownBy(() -> parser.parsear("{\"categoria\":\"OTRO\"}")).isInstanceOf(RespuestaIAInvalidaException.class);
        assertThatThrownBy(() -> parser.parsear("{\"categoria\":\"OTRO\",\"prioridad\":\"BAJA\",\"resumen\":\"x\","
                + "\"etiquetas\":[]}")).isInstanceOf(RespuestaIAInvalidaException.class);
        assertThatThrownBy(() -> parser.parsear(null)).isInstanceOf(RespuestaIAInvalidaException.class);
    }
}
