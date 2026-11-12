package com.tickets.application.service;

import com.tickets.application.port.in.AutenticarUseCase.ResultadoLogin;
import com.tickets.application.port.out.EmisorTokens;
import com.tickets.application.port.out.PasswordHasher;
import com.tickets.application.port.out.SesionRepository;
import com.tickets.domain.Actor;
import com.tickets.domain.Rol;
import com.tickets.domain.Sesion;
import com.tickets.domain.Usuario;
import com.tickets.domain.exception.CredencialesInvalidasException;
import com.tickets.support.InMemoryUsuarioRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AutenticacionServiceTest {

    Instant t0 = Instant.parse("2026-03-01T10:00:00Z");
    Clock reloj = Clock.fixed(t0, ZoneOffset.UTC);
    InMemoryUsuarioRepository usuarios = new InMemoryUsuarioRepository();
    List<Sesion> sesiones = new ArrayList<>();

    SesionRepository repoSesiones = new SesionRepository() {
        public void guardar(String hash, Long usuarioId, Instant expira) {
            sesiones.add(new Sesion((long) sesiones.size() + 1, hash, usuarioId, expira, false));
        }
        public Optional<Sesion> buscarPorHash(String hash) {
            return sesiones.stream().filter(s -> s.tokenHash().equals(hash)).findFirst();
        }
        public void revocar(String hash) {
            sesiones.replaceAll(s -> s.tokenHash().equals(hash)
                    ? new Sesion(s.id(), s.tokenHash(), s.usuarioId(), s.expiraEn(), true) : s);
        }
    };
    PasswordHasher hasher = new PasswordHasher() {
        public String hash(String p) { return "h:" + p; }
        public boolean coincide(String p, String h) { return h.equals("h:" + p); }
    };
    EmisorTokens emisor = new EmisorTokens() {
        public Token emitir(Usuario u) { return new Token("jwt-" + u.id(), t0.plusSeconds(3600)); }
        public Optional<Actor> validar(String jwt) {
            return jwt.equals("jwt-ok") ? Optional.of(new Actor(1L, Rol.ADMIN)) : Optional.empty();
        }
    };

    AutenticacionService service() {
        usuarios.items.put(1L, new Usuario(1L, "ADMIN", "admin@t.com", "h:pw", Rol.ADMIN, t0));
        return new AutenticacionService(usuarios, repoSesiones, hasher, emisor, reloj, Duration.ofHours(8));
    }

    @Test
    void loginCorrectoEmiteJwtYTokenDeSesion() {
        ResultadoLogin r = service().login("ADMIN@t.com", "pw");

        assertThat(r.accessToken()).isEqualTo("jwt-1");
        assertThat(r.sessionToken()).isNotBlank().hasSizeGreaterThan(30);
        assertThat(r.rol()).isEqualTo(Rol.ADMIN);
        assertThat(r.sessionExpiraEn()).isEqualTo(t0.plus(Duration.ofHours(8)));
        // se guarda el hash, nunca el token en claro
        assertThat(sesiones).hasSize(1);
        assertThat(sesiones.get(0).tokenHash()).isNotEqualTo(r.sessionToken()).hasSize(64);
    }

    @Test
    void passwordIncorrectoOUsuarioInexistenteDanElMismoError() {
        var s = service();
        assertThatThrownBy(() -> s.login("admin@t.com", "mal")).isInstanceOf(CredencialesInvalidasException.class);
        assertThatThrownBy(() -> s.login("nadie@t.com", "pw")).isInstanceOf(CredencialesInvalidasException.class);
        assertThat(sesiones).isEmpty();
    }

    @Test
    void autenticaPorTokenDeSesion() {
        var s = service();
        ResultadoLogin r = s.login("admin@t.com", "pw");
        assertThat(s.autenticarSesion(r.sessionToken())).contains(new Actor(1L, Rol.ADMIN));
        assertThat(s.autenticarSesion("otro-token")).isEmpty();
    }

    @Test
    void unaSesionRevocadaOVencidaNoAutentica() {
        var s = service();
        ResultadoLogin r = s.login("admin@t.com", "pw");
        s.cerrarSesion(r.sessionToken());
        assertThat(s.autenticarSesion(r.sessionToken())).isEmpty();

        var tarde = new AutenticacionService(usuarios, repoSesiones, hasher, emisor,
                Clock.fixed(t0.plus(Duration.ofHours(9)), ZoneOffset.UTC), Duration.ofHours(8));
        ResultadoLogin r2 = s.login("admin@t.com", "pw");
        assertThat(tarde.autenticarSesion(r2.sessionToken())).isEmpty();
    }

    @Test
    void autenticaPorJwtDelegandoEnElEmisor() {
        var s = service();
        assertThat(s.autenticarJwt("jwt-ok")).isPresent();
        assertThat(s.autenticarJwt("jwt-malo")).isEmpty();
    }
}
