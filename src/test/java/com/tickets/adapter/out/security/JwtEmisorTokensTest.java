package com.tickets.adapter.out.security;

import com.tickets.application.port.out.EmisorTokens;
import com.tickets.domain.Actor;
import com.tickets.domain.Rol;
import com.tickets.domain.Usuario;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtEmisorTokensTest {

    static final String SECRET = "un-secreto-de-prueba-con-mas-de-32-bytes-0123456789";
    Instant t0 = Instant.parse("2026-03-01T10:00:00Z");
    Usuario usuario = new Usuario(7L, "Sol", "sol@t.com", "h", Rol.SOPORTE, t0);

    JwtEmisorTokens emisor(String secret, Instant ahora) {
        return new JwtEmisorTokens(secret, Duration.ofMinutes(60), Clock.fixed(ahora, ZoneOffset.UTC));
    }

    @Test
    void emiteYValidaUnJwtConIdYRol() {
        var emisor = emisor(SECRET, t0);
        EmisorTokens.Token token = emisor.emitir(usuario);

        assertThat(token.expiraEn()).isEqualTo(t0.plus(Duration.ofMinutes(60)));
        assertThat(emisor.validar(token.valor())).contains(new Actor(7L, Rol.SOPORTE));
    }

    @Test
    void rechazaTokensExpirados() {
        String jwt = emisor(SECRET, t0).emitir(usuario).valor();
        assertThat(emisor(SECRET, t0.plus(Duration.ofMinutes(61))).validar(jwt)).isEmpty();
    }

    @Test
    void rechazaFirmasDeOtroSecretoYBasura() {
        String jwt = emisor(SECRET, t0).emitir(usuario).valor();
        assertThat(emisor("otro-secreto-distinto-con-mas-de-32-bytes-xxxxxxxx", t0).validar(jwt)).isEmpty();
        assertThat(emisor(SECRET, t0).validar("no.es.un.jwt")).isEmpty();
        assertThat(emisor(SECRET, t0).validar(jwt.substring(0, jwt.length() - 3) + "abc")).isEmpty();
    }

    @Test
    void rechazaUnJwtSinFirmaAlgNone() {
        String sinFirma = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString("{\"alg\":\"none\"}".getBytes())
                + "." + java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
                "{\"sub\":\"7\",\"rol\":\"ADMIN\",\"iss\":\"tickets-backend\",\"exp\":4102444800}".getBytes()) + ".";
        assertThat(emisor(SECRET, t0).validar(sinFirma)).isEmpty();
    }

    @Test
    void exigeUnSecretoDeAlMenos32Bytes() {
        assertThatThrownBy(() -> emisor("corto", t0)).isInstanceOf(IllegalArgumentException.class);
    }
}
