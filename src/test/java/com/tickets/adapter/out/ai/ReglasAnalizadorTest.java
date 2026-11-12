package com.tickets.adapter.out.ai;

import com.tickets.application.port.out.AnalisisTicketResponse;
import com.tickets.domain.OrigenAnalisis;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class ReglasAnalizadorTest {

    ReglasAnalizador analizador = new ReglasAnalizador();

    @Test
    void ejemploDelEnunciado() {
        AnalisisTicketResponse r = analizador.analizar("Problema con internet",
                "Desde esta mañana no puedo conectarme a internet y tengo una reunión importante en 20 minutos.");
        assertThat(r.categoria()).isEqualTo("CONECTIVIDAD");
        assertThat(r.prioridad()).isEqualTo("ALTA");
        assertThat(r.etiquetas()).contains("internet", "conectividad", "reunión");
        assertThat(r.origen()).isEqualTo(OrigenAnalisis.REGLAS);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Contraseña|Olvidé mi contraseña y no puedo hacer login en el portal|ACCESOS",
            "Pantalla|La pantalla de mi computador parpadea y el teclado no responde|HARDWARE",
            "Programa|La aplicación muestra un error al abrir el programa de facturación|SOFTWARE",
            "Wifi|El wifi de la oficina está muy lento y se cae la red constantemente|CONECTIVIDAD",
            "Consulta|Quisiera saber cuál es el horario de la cafetería del piso tres|OTRO"
    })
    void clasificaPorPalabrasClave(String titulo, String descripcion, String esperada) {
        assertThat(analizador.analizar(titulo, descripcion).categoria()).isEqualTo(esperada);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Caída|La producción detenida afecta a la línea completa del sistema|CRITICA",
            "Caída|Ningún servicio responde y están afectados todos los usuarios de la empresa|CRITICA",
            "Urgente|Necesito esto urgente, el sistema no abre para cerrar la venta|ALTA",
            "Duda|Cuando puedas, quisiera saber cómo cambiar el fondo de pantalla|BAJA",
            "Pregunta|Quisiera saber cuál es el horario de la cafetería del piso tres|MEDIA"
    })
    void asignaPrioridadPorPalabrasClave(String titulo, String descripcion, String esperada) {
        assertThat(analizador.analizar(titulo, descripcion).prioridad()).isEqualTo(esperada);
    }

    @Test
    void etiquetasEntreUnaYCincoYResumenDeMaximo30Palabras() {
        String larga = "El programa muestra un error. " + "palabra ".repeat(100);
        AnalisisTicketResponse r = analizador.analizar("Error", larga + " internet wifi red vpn pantalla teclado");
        assertThat(r.etiquetas()).hasSizeBetween(1, 5);
        assertThat(r.resumen().split("\\s+").length).isLessThanOrEqualTo(30);
    }
}
