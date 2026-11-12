package com.tickets.domain;

import com.tickets.domain.exception.SolicitudInvalidaException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EnumParserTest {

    @Test
    void parseaIgnorandoMayusculasYEspacios() {
        assertThat(EnumParser.parsear(Prioridad.class, " alta ", "Prioridad inválida")).isEqualTo(Prioridad.ALTA);
    }

    @Test
    void valorInvalidoListaLosPermitidos() {
        assertThatThrownBy(() -> EnumParser.parsear(Categoria.class, "XYZ", "Categoría inválida"))
                .isInstanceOf(SolicitudInvalidaException.class)
                .hasMessageContaining("Categoría inválida")
                .hasMessageContaining("CONECTIVIDAD");
    }

    @Test
    void valorVacioEsInvalido() {
        assertThatThrownBy(() -> EnumParser.parsear(EstadoTicket.class, "  ", "Estado inválido"))
                .isInstanceOf(SolicitudInvalidaException.class)
                .hasMessageContaining("Estado inválido");
    }
}
