package com.tickets.application.service;

import com.tickets.application.port.out.MetricasRepository;
import com.tickets.application.port.out.MetricasTickets;
import com.tickets.domain.Actor;
import com.tickets.domain.Rol;
import com.tickets.domain.exception.AccesoDenegadoException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MetricasServiceTest {

    @Mock MetricasRepository repositorio;
    @InjectMocks MetricasService service;

    MetricasTickets metricas = new MetricasTickets(3, Map.of("OTRO", 3L), Map.of("BAJA", 3L), Map.of("ABIERTO", 3L),
            Map.of("COMPLETADO", 3L), List.of(new MetricasTickets.Celda("OTRO", "BAJA", 3)));

    @Test
    void soporteYAdminConsultanLasMetricas() {
        when(repositorio.calcular()).thenReturn(metricas);
        assertThat(service.obtener(new Actor(1L, Rol.SOPORTE))).isSameAs(metricas);
        assertThat(service.obtener(new Actor(2L, Rol.ADMIN))).isSameAs(metricas);
    }

    @Test
    void unUsuarioNoPuedeYNoSeTocaLaBd() {
        assertThatThrownBy(() -> service.obtener(new Actor(3L, Rol.USUARIO))).isInstanceOf(AccesoDenegadoException.class);
        verifyNoInteractions(repositorio);
    }
}
