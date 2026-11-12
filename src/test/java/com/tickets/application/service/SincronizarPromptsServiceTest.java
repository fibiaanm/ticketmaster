package com.tickets.application.service;

import com.tickets.application.port.out.PromptDefinicion;
import com.tickets.application.port.out.PromptSource;
import com.tickets.domain.PromptTemplate;
import com.tickets.support.InMemoryPromptRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SincronizarPromptsServiceTest {

    InMemoryPromptRepository repo = new InMemoryPromptRepository();
    List<PromptDefinicion> definiciones = new ArrayList<>();
    PromptSource source = () -> definiciones;
    SincronizarPromptsService service = new SincronizarPromptsService(source, repo);

    @BeforeEach
    void setUp() {
        definiciones.add(new PromptDefinicion("ticket-analysis", 1, "sys v1", "user v1"));
    }

    @Test
    void cargaLaVersionNuevaYLaActiva() {
        service.sincronizar();
        PromptTemplate activo = repo.buscarActivo("ticket-analysis").orElseThrow();
        assertThat(activo.version()).isEqualTo(1);
        assertThat(activo.systemPrompt()).isEqualTo("sys v1");
        assertThat(activo.hash()).isNotBlank();
    }

    @Test
    void esIdempotente() {
        service.sincronizar();
        service.sincronizar();
        assertThat(repo.items).hasSize(1);
    }

    @Test
    void unaVersionNuevaMasAltaPasaAEstarActivaYLaAnteriorSeConserva() {
        service.sincronizar();
        definiciones.add(new PromptDefinicion("ticket-analysis", 2, "sys v2", "user v2"));
        service.sincronizar();

        assertThat(repo.items).hasSize(2);
        assertThat(repo.buscarActivo("ticket-analysis").orElseThrow().version()).isEqualTo(2);
        assertThat(repo.buscar("ticket-analysis", 1).orElseThrow().activo()).isFalse();
    }

    @Test
    void lasVersionesSonInmutables() {
        service.sincronizar();
        definiciones.set(0, new PromptDefinicion("ticket-analysis", 1, "sys v1 EDITADO", "user v1"));
        assertThatThrownBy(service::sincronizar)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("inmutable");
    }
}
