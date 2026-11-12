package com.tickets.support;

import com.tickets.application.port.out.PasswordHasher;
import com.tickets.application.port.out.UsuarioRepository;
import com.tickets.application.service.SincronizarPromptsService;
import com.tickets.domain.Rol;
import com.tickets.domain.Usuario;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class ApiTestBase {

    protected static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired protected MockMvc mvc;
    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected UsuarioRepository usuarios;
    @Autowired protected PasswordHasher hasher;
    @Autowired protected SincronizarPromptsService prompts;

    protected Usuario admin, soporte, usuario, otroUsuario;

    @BeforeEach
    void sembrar() {
        TestDb.limpiar(jdbc);
        prompts.sincronizar();
        admin = crear("ADMIN", Rol.ADMIN);
        soporte = crear("SOPORTE", Rol.SOPORTE);
        usuario = crear("USUARIO", Rol.USUARIO);
        otroUsuario = crear("OTRO", Rol.USUARIO);
    }

    private Usuario crear(String nombre, Rol rol) {
        return usuarios.guardar(new Usuario(null, nombre, nombre.toLowerCase() + "@tickets.local",
                hasher.hash("pw"), rol, Instant.now()));
    }

    protected JsonNode login(Usuario u) throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"correo\":\"%s\",\"password\":\"pw\"}".formatted(u.correo()))).andReturn();
        return JSON.readTree(r.getResponse().getContentAsString());
    }

    /** Agrega la credencial JWT del usuario. */
    protected MockHttpServletRequestBuilder jwt(MockHttpServletRequestBuilder req, Usuario u) throws Exception {
        return req.header("Authorization", "Bearer " + login(u).path("accessToken").asString());
    }

    /** Agrega la credencial de sesion (X-Ticket-Auth) del usuario. */
    protected MockHttpServletRequestBuilder sesion(MockHttpServletRequestBuilder req, Usuario u) throws Exception {
        return req.header("X-Ticket-Auth", login(u).path("sessionToken").asString());
    }

    protected static String ticketJson(String titulo, String descripcion, Long usuarioId) {
        return "{\"titulo\":\"%s\",\"descripcion\":\"%s\",\"usuarioId\":%s}".formatted(titulo, descripcion, usuarioId);
    }

    protected static final String DESC_INTERNET =
            "Desde esta mañana no puedo conectarme a internet y tengo una reunión importante en 20 minutos.";
}
