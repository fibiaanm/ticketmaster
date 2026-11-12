package com.tickets.adapter.in.web;

import com.tickets.support.ApiTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import tools.jackson.databind.JsonNode;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TicketApiIT extends ApiTestBase {

    long crearTicket(com.tickets.domain.Usuario actor, com.tickets.domain.Usuario solicitante) throws Exception {
        var res = mvc.perform(jwt(post("/api/tickets"), actor).contentType(MediaType.APPLICATION_JSON)
                .content(ticketJson("Problema con internet", DESC_INTERNET, solicitante.id()))).andExpect(status().isCreated())
                .andReturn();
        return JSON.readTree(res.getResponse().getContentAsString()).path("id").asLong();
    }

    // ---------------- autenticacion
    @Test
    void loginDevuelveJwtYTokenDeSesion() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"admin@tickets.local\",\"password\":\"pw\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", not(emptyString())))
                .andExpect(jsonPath("$.sessionToken", not(emptyString())))
                .andExpect(jsonPath("$.rol").value("ADMIN"));
    }

    @Test
    void loginInvalidoDa401ConElFormatoDeError() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"admin@tickets.local\",\"password\":\"mal\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.path").value("/api/auth/login"));
    }

    @Test
    void loginValidaElCorreo() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"no-es-correo\",\"password\":\"pw\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("correo no es válido")));
    }

    @Test
    void sinCredencialesDa401() throws Exception {
        mvc.perform(get("/api/tickets"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.path").value("/api/tickets"));
    }

    @Test
    void credencialesInvalidasDan401() throws Exception {
        mvc.perform(get("/api/tickets").header("Authorization", "Bearer basura")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/tickets").header("X-Ticket-Auth", "basura")).andExpect(status().isUnauthorized());
    }

    @Test
    void ambasCredencialesFuncionanYDanLaMismaIdentidad() throws Exception {
        mvc.perform(jwt(get("/api/tickets"), admin)).andExpect(status().isOk());
        mvc.perform(sesion(get("/api/tickets"), admin)).andExpect(status().isOk());
    }

    @Test
    void logoutRevocaLaSesion() throws Exception {
        String token = login(admin).path("sessionToken").asString();
        mvc.perform(get("/api/tickets").header("X-Ticket-Auth", token)).andExpect(status().isOk());
        mvc.perform(post("/api/auth/logout").header("X-Ticket-Auth", token)).andExpect(status().isNoContent());
        mvc.perform(get("/api/tickets").header("X-Ticket-Auth", token)).andExpect(status().isUnauthorized());
    }

    // ---------------- crear
    @Test
    void creaUnTicketYElAnalisisQuedaGuardado() throws Exception {
        mvc.perform(jwt(post("/api/tickets"), usuario).contentType(MediaType.APPLICATION_JSON)
                        .content(ticketJson("Problema con internet", DESC_INTERNET, usuario.id())))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", startsWith("/api/tickets/")))
                .andExpect(jsonPath("$.estado").value("ABIERTO"))
                .andExpect(jsonPath("$.solicitante.id").value(usuario.id()))
                // modo sync de pruebas + sin API key -> el fallback local clasifica
                .andExpect(jsonPath("$.estadoAnalisis").value("COMPLETADO"))
                .andExpect(jsonPath("$.categoria").value("CONECTIVIDAD"))
                .andExpect(jsonPath("$.prioridad").value("ALTA"))
                .andExpect(jsonPath("$.etiquetasIA", hasItems("internet", "conectividad")))
                .andExpect(jsonPath("$.resumenIA", not(emptyString())));
    }

    @Test
    void validaTituloYDescripcion() throws Exception {
        mvc.perform(jwt(post("/api/tickets"), usuario).contentType(MediaType.APPLICATION_JSON)
                        .content(ticketJson("abc", "corta", usuario.id())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message", containsString("título debe tener entre 5 y 100")))
                .andExpect(jsonPath("$.message", containsString("descripción debe tener entre 20 y 2000")));

        mvc.perform(jwt(post("/api/tickets"), usuario).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("título es obligatorio")))
                .andExpect(jsonPath("$.message", containsString("descripción es obligatoria")))
                .andExpect(jsonPath("$.message", containsString("usuarioId es obligatorio")));
    }

    @Test
    void cuerpoMalFormadoDa400() throws Exception {
        mvc.perform(jwt(post("/api/tickets"), usuario).contentType(MediaType.APPLICATION_JSON).content("{no json"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("BAD_REQUEST"));
    }

    @Test
    void usuarioSolicitanteInexistenteDa404() throws Exception {
        mvc.perform(jwt(post("/api/tickets"), admin).contentType(MediaType.APPLICATION_JSON)
                        .content(ticketJson("Problema con internet", DESC_INTERNET, 99999L)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("No se encontró el usuario con id 99999"));
    }

    @Test
    void unUsuarioNoPuedeCrearEnNombreDeOtro() throws Exception {
        mvc.perform(jwt(post("/api/tickets"), usuario).contentType(MediaType.APPLICATION_JSON)
                        .content(ticketJson("Problema con internet", DESC_INTERNET, otroUsuario.id())))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    // ---------------- consultar
    @Test
    void ticketInexistenteDa404ConElFormatoDelEnunciado() throws Exception {
        mvc.perform(jwt(get("/api/tickets/15"), admin))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.timestamp", not(emptyString())))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("No se encontró el ticket con id 15"))
                .andExpect(jsonPath("$.path").value("/api/tickets/15"));
    }

    @Test
    void idNoNumericoDa400() throws Exception {
        mvc.perform(jwt(get("/api/tickets/abc"), admin)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("'id'")));
    }

    @Test
    void consultaUnTicketConSuAnalisisCompleto() throws Exception {
        long id = crearTicket(usuario, usuario);
        mvc.perform(jwt(get("/api/tickets/" + id), usuario))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.categoria").value("CONECTIVIDAD"))
                .andExpect(jsonPath("$.resumenIA", not(emptyString())))
                .andExpect(jsonPath("$.etiquetasIA", not(empty())));
    }

    @Test
    void unUsuarioNoVeTicketsAjenosPeroSoporteSi() throws Exception {
        long id = crearTicket(usuario, usuario);
        mvc.perform(jwt(get("/api/tickets/" + id), otroUsuario)).andExpect(status().isNotFound());
        mvc.perform(jwt(get("/api/tickets/" + id), soporte)).andExpect(status().isOk());
    }

    @Test
    void listaConFiltros() throws Exception {
        long a = crearTicket(usuario, usuario);   // CONECTIVIDAD / ALTA
        crearTicket(otroUsuario, otroUsuario);
        mvc.perform(jwt(patch("/api/tickets/" + a + "/estado"), soporte).contentType(MediaType.APPLICATION_JSON)
                .content("{\"estado\":\"EN_PROCESO\"}")).andExpect(status().isOk());

        mvc.perform(jwt(get("/api/tickets"), soporte)).andExpect(jsonPath("$.totalElementos").value(2));
        mvc.perform(jwt(get("/api/tickets?estado=EN_PROCESO"), soporte))
                .andExpect(jsonPath("$.totalElementos").value(1)).andExpect(jsonPath("$.contenido[0].id").value(a));
        mvc.perform(jwt(get("/api/tickets?estado=ABIERTO&prioridad=ALTA"), soporte))
                .andExpect(jsonPath("$.totalElementos").value(1));
        mvc.perform(jwt(get("/api/tickets?categoria=HARDWARE"), soporte)).andExpect(jsonPath("$.totalElementos").value(0));
        mvc.perform(jwt(get("/api/tickets?usuarioId=" + otroUsuario.id()), soporte))
                .andExpect(jsonPath("$.totalElementos").value(1));
        // ignora mayusculas
        mvc.perform(jwt(get("/api/tickets?estado=en_proceso"), soporte)).andExpect(jsonPath("$.totalElementos").value(1));
    }

    @Test
    void filtrosInvalidosDan400ConMensajeEspecifico() throws Exception {
        mvc.perform(jwt(get("/api/tickets?estado=VOLANDO"), admin)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", startsWith("Estado inválido")));
        mvc.perform(jwt(get("/api/tickets?categoria=MAGIA"), admin)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", startsWith("Categoría inválida")));
        mvc.perform(jwt(get("/api/tickets?prioridad=URGENTISIMA"), admin)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", startsWith("Prioridad inválida")));
        mvc.perform(jwt(get("/api/tickets?size=0"), admin)).andExpect(status().isBadRequest());
        mvc.perform(jwt(get("/api/tickets?usuarioId=abc"), admin)).andExpect(status().isBadRequest());
    }

    @Test
    void unUsuarioSoloListaSusTickets() throws Exception {
        crearTicket(usuario, usuario);
        crearTicket(otroUsuario, otroUsuario);
        mvc.perform(jwt(get("/api/tickets"), usuario)).andExpect(jsonPath("$.totalElementos").value(1));
        mvc.perform(jwt(get("/api/tickets?usuarioId=" + otroUsuario.id()), usuario))
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.contenido[0].solicitante.id").value(usuario.id()));
    }

    // ---------------- estado
    @Test
    void actualizaElEstado() throws Exception {
        long id = crearTicket(usuario, usuario);
        mvc.perform(jwt(patch("/api/tickets/" + id + "/estado"), soporte).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"EN_PROCESO\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("EN_PROCESO"));
    }

    @Test
    void estadoInvalidoOTransicionInvalidaDan400() throws Exception {
        long id = crearTicket(usuario, usuario);
        mvc.perform(jwt(patch("/api/tickets/" + id + "/estado"), soporte).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"VOLANDO\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message", startsWith("Estado inválido")));
        mvc.perform(jwt(patch("/api/tickets/" + id + "/estado"), soporte).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"RESUELTO\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message", containsString("ABIERTO a RESUELTO")));
        mvc.perform(jwt(patch("/api/tickets/" + id + "/estado"), soporte).contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message", containsString("estado es obligatorio")));
    }

    @Test
    void unUsuarioNoPuedeCambiarElEstado() throws Exception {
        long id = crearTicket(usuario, usuario);
        mvc.perform(jwt(patch("/api/tickets/" + id + "/estado"), usuario).contentType(MediaType.APPLICATION_JSON)
                .content("{\"estado\":\"EN_PROCESO\"}")).andExpect(status().isForbidden());
    }

    // ---------------- asignar
    @Test
    void adminAsignaUnTecnicoDeSoporte() throws Exception {
        long id = crearTicket(usuario, usuario);
        mvc.perform(jwt(patch("/api/tickets/" + id + "/asignar"), admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tecnicoId\":" + soporte.id() + "}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.tecnicoAsignado.id").value(soporte.id()));
    }

    @Test
    void asignarUnTecnicoInvalidoDa400() throws Exception {
        long id = crearTicket(usuario, usuario);
        mvc.perform(jwt(patch("/api/tickets/" + id + "/asignar"), admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tecnicoId\":" + usuario.id() + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("no tiene rol SOPORTE")));
        mvc.perform(jwt(patch("/api/tickets/" + id + "/asignar"), admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tecnicoId\":99999}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void soloAdminAsigna() throws Exception {
        long id = crearTicket(usuario, usuario);
        mvc.perform(jwt(patch("/api/tickets/" + id + "/asignar"), soporte).contentType(MediaType.APPLICATION_JSON)
                .content("{\"tecnicoId\":" + soporte.id() + "}")).andExpect(status().isForbidden());
    }

    // ---------------- eliminar (logico)
    @Test
    void adminEliminaLogicamenteYYaNoSeConsulta() throws Exception {
        long id = crearTicket(usuario, usuario);
        mvc.perform(jwt(delete("/api/tickets/" + id), admin)).andExpect(status().isNoContent());
        mvc.perform(jwt(get("/api/tickets/" + id), admin)).andExpect(status().isNotFound());
        mvc.perform(jwt(get("/api/tickets"), admin)).andExpect(jsonPath("$.totalElementos").value(0));
        org.assertj.core.api.Assertions.assertThat(
                jdbc.queryForObject("SELECT eliminado_en IS NOT NULL FROM ticket WHERE id = ?", Boolean.class, id)).isTrue();
    }

    @Test
    void soloAdminElimina() throws Exception {
        long id = crearTicket(usuario, usuario);
        mvc.perform(jwt(delete("/api/tickets/" + id), soporte)).andExpect(status().isForbidden());
        mvc.perform(jwt(delete("/api/tickets/" + id), usuario)).andExpect(status().isForbidden());
    }

    // ---------------- observabilidad
    @Test
    void devuelveElRequestIdYRespetaUnoValidoEntrante() throws Exception {
        mvc.perform(jwt(get("/api/tickets"), admin).header("X-Request-Id", "abc-12345678"))
                .andExpect(header().string("X-Request-Id", "abc-12345678"));
        mvc.perform(get("/api/tickets").header("X-Request-Id", "<script>")).andExpect(header().string("X-Request-Id", not("<script>")));
    }
}
