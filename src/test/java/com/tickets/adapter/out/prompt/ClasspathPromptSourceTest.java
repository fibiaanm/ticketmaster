package com.tickets.adapter.out.prompt;

import com.tickets.application.port.out.PromptDefinicion;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ClasspathPromptSourceTest {

    @Test
    void cargaLasVersionesDesdeArchivos() {
        List<PromptDefinicion> defs = new ClasspathPromptSource().cargar();
        PromptDefinicion v1 = defs.stream()
                .filter(d -> d.nombre().equals("ticket-analysis") && d.version() == 1).findFirst().orElseThrow();
        assertThat(v1.systemPrompt()).contains("JSON");
        assertThat(v1.userTemplate()).contains("{titulo}").contains("{descripcion}");
    }
}
