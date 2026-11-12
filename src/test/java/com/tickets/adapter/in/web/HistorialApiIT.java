package com.tickets.adapter.in.web;

import com.tickets.support.ApiTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class HistorialApiIT extends ApiTestBase {

    long crear() throws Exception {
        var res = mvc.perform(jwt(post("/api/tickets"), usuario).contentType(MediaType.APPLICATION_JSON)
                .content(ticketJson("Problema con internet", DESC_INTERNET, usuario.id()))).andReturn();
        return JSON.readTree(res.getResponse().getContentAsString()).path("id").asLong();
    }

    @Test
    void elHistorialRegistraCadaCambioDeEstadoConQuienLoHizo() throws Exception {
        long id = crear();
        mvc.perform(jwt(patch("/api/tickets/" + id + "/estado"), soporte).contentType(MediaType.APPLICATION_JSON)
                .content("{\"estado\":\"EN_PROCESO\"}")).andExpect(status().isOk());
        mvc.perform(jwt(patch("/api/tickets/" + id + "/estado"), soporte).contentType(MediaType.APPLICATION_JSON)
                .content("{\"estado\":\"RESUELTO\"}")).andExpect(status().isOk());

        mvc.perform(jwt(get("/api/tickets/" + id + "/historial"), usuario))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].tipo").value("ESTADO_CAMBIADO"))
                .andExpect(jsonPath("$[0].detalle.de").value("ABIERTO"))
                .andExpect(jsonPath("$[0].detalle.a").value("EN_PROCESO"))
                .andExpect(jsonPath("$[0].actor.rol").value("SOPORTE"))
                .andExpect(jsonPath("$[1].detalle.a").value("RESUELTO"));
    }

    @Test
    void unUsuarioNoVeElHistorialAjeno() throws Exception {
        long id = crear();
        mvc.perform(jwt(get("/api/tickets/" + id + "/historial"), otroUsuario)).andExpect(status().isNotFound());
    }

    @Test
    void lineaDeTiempoIncluyeElAnalisisConElModeloYElPrompt() throws Exception {
        long id = crear();
        mvc.perform(jwt(get("/api/tickets/" + id + "/linea-tiempo"), soporte))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].tipo", contains("TICKET_CREADO", "ANALISIS_COMPLETADO")))
                .andExpect(jsonPath("$[1].actor").value(nullValue()))
                .andExpect(jsonPath("$[1].detalle.analizadoPor").value("REGLAS"))
                .andExpect(jsonPath("$[1].detalle.categoria").value("CONECTIVIDAD"));
        mvc.perform(jwt(get("/api/tickets/" + id + "/linea-tiempo"), usuario)).andExpect(status().isForbidden());
    }

    @Test
    void laAuditoriaEsSoloParaAdminYSeFiltra() throws Exception {
        long id = crear();
        mvc.perform(jwt(patch("/api/tickets/" + id + "/asignar"), admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"tecnicoId\":" + soporte.id() + "}")).andExpect(status().isOk());

        mvc.perform(jwt(get("/api/auditoria"), admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(3)); // creado, analisis, asignado
        mvc.perform(jwt(get("/api/auditoria?tipo=TECNICO_ASIGNADO&actorId=" + admin.id()), admin))
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.contenido[0].detalle.nuevo").value(soporte.id()));
        mvc.perform(jwt(get("/api/auditoria?tipo=INVENTADO"), admin)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", startsWith("Tipo de evento inválido")));
        mvc.perform(jwt(get("/api/auditoria"), soporte)).andExpect(status().isForbidden());
        mvc.perform(jwt(get("/api/auditoria"), usuario)).andExpect(status().isForbidden());
    }

    @Test
    void reanalizarEsSoloParaAdminDevuelve202YDejaRastro() throws Exception {
        long id = crear();   // modo sync de pruebas: queda COMPLETADO
        mvc.perform(jwt(post("/api/tickets/" + id + "/reanalizar"), soporte)).andExpect(status().isForbidden());
        mvc.perform(jwt(post("/api/tickets/" + id + "/reanalizar"), admin))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.estadoAnalisis").value("COMPLETADO"));

        mvc.perform(jwt(get("/api/tickets/" + id + "/linea-tiempo"), admin))
                .andExpect(jsonPath("$[*].tipo", contains("TICKET_CREADO", "ANALISIS_COMPLETADO",
                        "REANALISIS_SOLICITADO", "ANALISIS_COMPLETADO")));
        mvc.perform(jwt(post("/api/tickets/99999/reanalizar"), admin)).andExpect(status().isNotFound());
    }

    @Test
    void noSePuedeReanalizarUnAnalisisEnCurso() throws Exception {
        long id = crear();
        jdbc.update("UPDATE ticket SET estado_analisis = 'ANALIZANDO' WHERE id = ?", id);
        mvc.perform(jwt(post("/api/tickets/" + id + "/reanalizar"), admin)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("en curso")));
    }

    @Test
    void elTicketFallidoSePuedeReanalizar() throws Exception {
        long id = crear();
        jdbc.update("UPDATE ticket SET estado_analisis = 'FALLIDO' WHERE id = ?", id);
        mvc.perform(jwt(post("/api/tickets/" + id + "/reanalizar"), admin)).andExpect(status().isAccepted());
        assertThat(jdbc.queryForObject("SELECT estado_analisis FROM ticket WHERE id = ?", String.class, id)).isEqualTo("COMPLETADO");
    }

    @Test
    void conflictoDeVersionSeVeComoDatoVersionadoEnLaBd() throws Exception {
        long id = crear();
        Long v1 = jdbc.queryForObject("SELECT version FROM ticket WHERE id = ?", Long.class, id);
        mvc.perform(jwt(patch("/api/tickets/" + id + "/estado"), soporte).contentType(MediaType.APPLICATION_JSON)
                .content("{\"estado\":\"EN_PROCESO\"}")).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT version FROM ticket WHERE id = ?", Long.class, id)).isGreaterThan(v1);
    }
}
