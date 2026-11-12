package com.tickets.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PromptTemplateTest {

    @Test
    void renderReemplazaPlaceholdersEnUnaSolaPasada() {
        PromptTemplate p = new PromptTemplate(1L, "ticket-analysis", 1, "sys", "T={titulo} D={descripcion}", true, "h");
        // la descripcion contiene un placeholder: no debe reemplazarse de nuevo
        assertThat(p.renderUsuario("Hola", "texto {titulo} raro")).isEqualTo("T=Hola D=texto {titulo} raro");
    }
}
