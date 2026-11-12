package com.tickets.domain;

import com.tickets.domain.exception.SolicitudInvalidaException;
import com.tickets.domain.exception.TecnicoInvalidoException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TicketTest {

    static final Instant T0 = Instant.parse("2026-01-01T10:00:00Z");
    static final Instant T1 = Instant.parse("2026-01-01T11:00:00Z");

    Ticket ticket() {
        return Ticket.nuevo("Problema con internet", "Desde esta mañana no puedo conectarme a internet", 1L, T0);
    }

    @Test
    void nuevoTicketNaceAbiertoYPendienteDeAnalisis() {
        Ticket t = ticket();
        assertThat(t.getEstado()).isEqualTo(EstadoTicket.ABIERTO);
        assertThat(t.getEstadoAnalisis()).isEqualTo(EstadoAnalisis.PENDIENTE);
        assertThat(t.getCategoria()).isNull();
        assertThat(t.getPrioridad()).isNull();
        assertThat(t.getFechaCreacion()).isEqualTo(T0);
        assertThat(t.getFechaActualizacion()).isEqualTo(T0);
        assertThat(t.getEtiquetasIA()).isEmpty();
    }

    @Test
    void cambiarEstadoValidoActualizaFecha() {
        Ticket t = ticket();
        t.cambiarEstado(EstadoTicket.EN_PROCESO, T1);
        assertThat(t.getEstado()).isEqualTo(EstadoTicket.EN_PROCESO);
        assertThat(t.getFechaActualizacion()).isEqualTo(T1);
    }

    @Test
    void cambiarEstadoConTransicionInvalidaFalla() {
        Ticket t = ticket();
        assertThatThrownBy(() -> t.cambiarEstado(EstadoTicket.RESUELTO, T1))
                .isInstanceOf(SolicitudInvalidaException.class)
                .hasMessageContaining("ABIERTO")
                .hasMessageContaining("RESUELTO");
    }

    @Test
    void soloUnUsuarioSoporteSePuedeAsignarComoTecnico() {
        Ticket t = ticket();
        Usuario usuario = new Usuario(2L, "Ana", "ana@x.com", "h", Rol.USUARIO, T0);
        assertThatThrownBy(() -> t.asignarTecnico(usuario, T1)).isInstanceOf(TecnicoInvalidoException.class);

        Usuario soporte = new Usuario(5L, "Sol", "sol@x.com", "h", Rol.SOPORTE, T0);
        t.asignarTecnico(soporte, T1);
        assertThat(t.getTecnicoAsignadoId()).isEqualTo(5L);
    }

    @Test
    void completarAnalisisGuardaResultadoYTrazabilidad() {
        Ticket t = ticket();
        t.iniciarAnalisis(T1);
        assertThat(t.getEstadoAnalisis()).isEqualTo(EstadoAnalisis.ANALIZANDO);

        t.completarAnalisis(Categoria.CONECTIVIDAD, Prioridad.ALTA, "Sin internet",
                List.of("internet", "reunión"), OrigenAnalisis.LLM, "gpt-x", 7L, T1);

        assertThat(t.getEstadoAnalisis()).isEqualTo(EstadoAnalisis.COMPLETADO);
        assertThat(t.getCategoria()).isEqualTo(Categoria.CONECTIVIDAD);
        assertThat(t.getPrioridad()).isEqualTo(Prioridad.ALTA);
        assertThat(t.getResumenIA()).isEqualTo("Sin internet");
        assertThat(t.getEtiquetasIA()).containsExactly("internet", "reunión");
        assertThat(t.getAnalizadoPor()).isEqualTo(OrigenAnalisis.LLM);
        assertThat(t.getModeloIA()).isEqualTo("gpt-x");
        assertThat(t.getPromptTemplateId()).isEqualTo(7L);
    }

    @Test
    void fallarAnalisisMarcaFallido() {
        Ticket t = ticket();
        t.fallarAnalisis(T1);
        assertThat(t.getEstadoAnalisis()).isEqualTo(EstadoAnalisis.FALLIDO);
    }

    @Test
    void eliminarEsLogico() {
        Ticket t = ticket();
        assertThat(t.estaEliminado()).isFalse();
        t.eliminar(T1);
        assertThat(t.estaEliminado()).isTrue();
        assertThat(t.getEliminadoEn()).isEqualTo(T1);
    }

    @Test
    void visibilidadPorRol() {
        Ticket t = ticket(); // solicitante 1
        assertThat(t.esVisiblePara(new Actor(1L, Rol.USUARIO))).isTrue();
        assertThat(t.esVisiblePara(new Actor(2L, Rol.USUARIO))).isFalse();
        assertThat(t.esVisiblePara(new Actor(2L, Rol.SOPORTE))).isTrue();
        assertThat(t.esVisiblePara(new Actor(3L, Rol.ADMIN))).isTrue();
    }

    @Test
    void soloSePuedeReanalizarCuandoElAnalisisTerminoOFallo() {
        Ticket t = ticket();
        assertThatThrownBy(() -> t.solicitarReanalisis(T1)).isInstanceOf(SolicitudInvalidaException.class); // PENDIENTE
        t.iniciarAnalisis(T1);
        assertThatThrownBy(() -> t.solicitarReanalisis(T1)).isInstanceOf(SolicitudInvalidaException.class); // ANALIZANDO

        t.fallarAnalisis(T1);
        t.solicitarReanalisis(T1);
        assertThat(t.getEstadoAnalisis()).isEqualTo(EstadoAnalisis.PENDIENTE);
    }
}
