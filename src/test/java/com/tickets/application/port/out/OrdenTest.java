package com.tickets.application.port.out;

import com.tickets.domain.exception.SolicitudInvalidaException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrdenTest {

    @Test
    void parseaCampoYDireccion() {
        assertThat(Orden.parsear("prioridad,desc")).isEqualTo(new Orden(Orden.Campo.PRIORIDAD, Orden.Direccion.DESC));
        assertThat(Orden.parsear("fechaCreacion,ASC")).isEqualTo(new Orden(Orden.Campo.FECHA_CREACION, Orden.Direccion.ASC));
        assertThat(Orden.parsear(" titulo ")).isEqualTo(new Orden(Orden.Campo.TITULO, Orden.Direccion.ASC));
    }

    @Test
    void sinValorNoHayOrden() {
        assertThat(Orden.parsear(null)).isNull();
        assertThat(Orden.parsear("  ")).isNull();
    }

    @Test
    void rechazaCamposFueraDeLaListaBlancaYDireccionesInvalidas() {
        assertThatThrownBy(() -> Orden.parsear("password,asc")).isInstanceOf(SolicitudInvalidaException.class)
                .hasMessageContaining("Orden inválido").hasMessageContaining("fechaCreacion");
        assertThatThrownBy(() -> Orden.parsear("titulo,sideways")).isInstanceOf(SolicitudInvalidaException.class);
        assertThatThrownBy(() -> Orden.parsear("id,asc,extra")).isInstanceOf(SolicitudInvalidaException.class);
        assertThatThrownBy(() -> Orden.parsear("id; DROP TABLE ticket")).isInstanceOf(SolicitudInvalidaException.class);
    }
}
