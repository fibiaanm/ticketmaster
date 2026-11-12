package com.tickets.adapter.in.web;

import com.tickets.support.ApiTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestPropertySource(properties = {"ratelimit.login=3", "ratelimit.crear-ticket=2", "ratelimit.general=8",
        "ratelimit.window=10m"})
class RateLimitIT extends ApiTestBase {

    private final java.util.concurrent.atomic.AtomicInteger siguienteIp = new java.util.concurrent.atomic.AtomicInteger(100);

    /** Cada login auxiliar sale de una IP distinta para no consumir el cupo de login que prueban estos tests. */
    @Override
    protected tools.jackson.databind.JsonNode login(com.tickets.domain.Usuario u) throws Exception {
        String ip = "172.16.0." + siguienteIp.incrementAndGet();
        var r = mvc.perform(post("/api/auth/login").with(req -> { req.setRemoteAddr(ip); return req; })
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"correo\":\"%s\",\"password\":\"pw\"}".formatted(u.correo()))).andReturn();
        return JSON.readTree(r.getResponse().getContentAsString());
    }

    String login(String ip, String password) {
        return "{\"correo\":\"admin@tickets.local\",\"password\":\"" + password + "\"}";
    }

    @Test
    void elLoginSeLimitaPorIpContraLaFuerzaBruta() throws Exception {
        for (int i = 0; i < 3; i++) {
            mvc.perform(post("/api/auth/login").with(r -> { r.setRemoteAddr("10.0.0.1"); return r; })
                    .contentType(MediaType.APPLICATION_JSON).content(login("", "incorrecta")))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/auth/login").with(r -> { r.setRemoteAddr("10.0.0.1"); return r; })
                        .contentType(MediaType.APPLICATION_JSON).content(login("", "pw")))   // aun con la clave correcta
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(header().string("X-RateLimit-Remaining", "0"))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.error").value("TOO_MANY_REQUESTS"))
                .andExpect(jsonPath("$.path").value("/api/auth/login"));

        // otra IP no se ve afectada
        mvc.perform(post("/api/auth/login").with(r -> { r.setRemoteAddr("10.0.0.2"); return r; })
                .contentType(MediaType.APPLICATION_JSON).content(login("", "pw"))).andExpect(status().isOk());
    }

    @Test
    void laCreacionDeTicketsSeLimitaPorUsuario() throws Exception {
        String jwtUsuario = "Bearer " + login(usuario).path("accessToken").asString();
        String jwtOtro = "Bearer " + login(otroUsuario).path("accessToken").asString();
        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/api/tickets").header("Authorization", jwtUsuario).contentType(MediaType.APPLICATION_JSON)
                    .content(ticketJson("Problema con internet", DESC_INTERNET, usuario.id()))).andExpect(status().isCreated());
        }
        mvc.perform(post("/api/tickets").header("Authorization", jwtUsuario).contentType(MediaType.APPLICATION_JSON)
                        .content(ticketJson("Problema con internet", DESC_INTERNET, usuario.id())))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message", containsString("Demasiadas peticiones")));

        // otro usuario conserva su cupo
        mvc.perform(post("/api/tickets").header("Authorization", jwtOtro).contentType(MediaType.APPLICATION_JSON)
                .content(ticketJson("Problema con internet", DESC_INTERNET, otroUsuario.id()))).andExpect(status().isCreated());
    }

    @Test
    void elLimiteGeneralAplicaAlResto() throws Exception {
        String jwt = "Bearer " + login(admin).path("accessToken").asString();   // consume 1 del limite del login (por IP)
        int permitidas = 0;
        for (int i = 0; i < 12; i++) {
            int estado = mvc.perform(get("/api/tickets").header("Authorization", jwt)).andReturn().getResponse().getStatus();
            if (estado == 200) permitidas++;
        }
        org.assertj.core.api.Assertions.assertThat(permitidas).isEqualTo(8);
    }

    @Test
    void informaElCupoRestanteEnCadaRespuesta() throws Exception {
        String jwt = "Bearer " + login(soporte).path("accessToken").asString();
        mvc.perform(get("/api/tickets").header("Authorization", jwt))
                .andExpect(status().isOk())
                .andExpect(header().string("X-RateLimit-Limit", "8"))
                .andExpect(header().string("X-RateLimit-Remaining", "7"));
    }

    @Test
    void lasRutasFueraDeApiNoSeLimitan() throws Exception {
        for (int i = 0; i < 20; i++) {
            mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        }
    }
}
