package com.tickets.adapter.in.web;

import com.tickets.support.ApiTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ListadoApiIT extends ApiTestBase {

    void crear(String titulo, String descripcion) throws Exception {
        mvc.perform(jwt(post("/api/tickets"), usuario).contentType(MediaType.APPLICATION_JSON)
                .content(ticketJson(titulo, descripcion, usuario.id()))).andExpect(status().isCreated());
    }

    @Test
    void ordenaPorPrioridadDeMayorAMenor() throws Exception {
        crear("Duda sencilla", "Cuando puedas, quisiera saber como cambiar el fondo de pantalla");     // BAJA
        crear("Servidor caido", "La produccion detenida afecta a todos los usuarios de la empresa");  // CRITICA
        crear("Internet lento", "Desde esta mañana no puedo conectarme a internet, es urgente");      // ALTA

        mvc.perform(jwt(get("/api/tickets?sort=prioridad,desc"), soporte))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido[*].prioridad", contains("CRITICA", "ALTA", "BAJA")));
        mvc.perform(jwt(get("/api/tickets?sort=prioridad,asc"), soporte))
                .andExpect(jsonPath("$.contenido[*].prioridad", contains("BAJA", "ALTA", "CRITICA")));
    }

    @Test
    void buscaPorTextoConQ() throws Exception {
        crear("Problema con internet", "Desde esta mañana no puedo conectarme a internet y tengo una reunion");
        crear("Impresora atascada", "La impresora del segundo piso atasca el papel cada vez que imprimo");

        mvc.perform(jwt(get("/api/tickets?q=impresora"), soporte))
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.contenido[0].titulo").value("Impresora atascada"));
        mvc.perform(jwt(get("/api/tickets?q=REUNIÓN"), soporte)).andExpect(jsonPath("$.totalElementos").value(1));
        mvc.perform(jwt(get("/api/tickets?q=%25"), soporte)).andExpect(jsonPath("$.totalElementos").value(0));
    }

    @Test
    void combinaTextoFechasFiltroYOrden() throws Exception {
        crear("Internet uno", "Desde esta mañana no puedo conectarme a internet y tengo una reunion");
        crear("Internet dos", "El wifi de la oficina esta muy lento y se cae la red constantemente");
        crear("Impresora", "La impresora del segundo piso atasca el papel cada vez que imprimo");

        mvc.perform(jwt(get("/api/tickets?q=internet&categoria=CONECTIVIDAD&desde=2000-01-01T00:00:00&sort=titulo,desc"), soporte))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido[*].titulo", contains("Internet uno", "Internet dos")));
        mvc.perform(jwt(get("/api/tickets?q=internet&desde=2099-01-01T00:00:00"), soporte))
                .andExpect(jsonPath("$.totalElementos").value(0));
        mvc.perform(jwt(get("/api/tickets?hasta=2000-01-01T00:00:00"), soporte))
                .andExpect(jsonPath("$.totalElementos").value(0));
    }

    @Test
    void elTextoNoSaltaseElAlcanceDelUsuario() throws Exception {
        crear("Internet uno", "Desde esta mañana no puedo conectarme a internet y tengo una reunion");
        mvc.perform(jwt(post("/api/tickets"), otroUsuario).contentType(MediaType.APPLICATION_JSON)
                .content(ticketJson("Internet otro", "Mi internet tambien esta fallando desde hace horas", otroUsuario.id())));

        mvc.perform(jwt(get("/api/tickets?q=internet"), usuario))
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.contenido[0].solicitante.id").value(usuario.id()));
    }

    @Test
    void parametrosInvalidosDan400() throws Exception {
        mvc.perform(jwt(get("/api/tickets?sort=password"), soporte)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", startsWith("Orden inválido")));
        mvc.perform(jwt(get("/api/tickets?sort=titulo,sideways"), soporte)).andExpect(status().isBadRequest());
        mvc.perform(jwt(get("/api/tickets?desde=ayer"), soporte)).andExpect(status().isBadRequest());
        mvc.perform(jwt(get("/api/tickets?desde=2026-02-01T00:00:00&hasta=2026-01-01T00:00:00"), soporte))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message", containsString("rango de fechas")));
        mvc.perform(jwt(get("/api/tickets?q=" + "x".repeat(101)), soporte)).andExpect(status().isBadRequest());
    }

    @Test
    void metricasPorCategoriaYPrioridadSoloParaSoporteYAdmin() throws Exception {
        crear("Problema con internet", "Desde esta mañana no puedo conectarme a internet y tengo una reunion importante");
        crear("Servidor caido", "La produccion detenida afecta a todos los usuarios de la empresa y del sistema");

        mvc.perform(jwt(get("/api/tickets/metricas"), soporte))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.porCategoria.CONECTIVIDAD").value(1))
                .andExpect(jsonPath("$.porCategoria.HARDWARE").value(0))
                .andExpect(jsonPath("$.porPrioridad.ALTA").value(1))
                .andExpect(jsonPath("$.porPrioridad.CRITICA").value(1))
                .andExpect(jsonPath("$.porEstado.ABIERTO").value(2))
                .andExpect(jsonPath("$.categoriaPorPrioridad[?(@.categoria=='CONECTIVIDAD' && @.prioridad=='ALTA')].total",
                        contains(1)));
        mvc.perform(jwt(get("/api/tickets/metricas"), admin)).andExpect(status().isOk());
        mvc.perform(jwt(get("/api/tickets/metricas"), usuario)).andExpect(status().isForbidden());
    }
}
