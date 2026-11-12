package com.tickets.adapter.out.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.tickets.application.port.out.EmisorTokens;
import com.tickets.domain.Actor;
import com.tickets.domain.Rol;
import com.tickets.domain.Usuario;

import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

/** JWT firmado con HS256. Solo acepta ese algoritmo (rechaza "none" y otros). */
public class JwtEmisorTokens implements EmisorTokens {

    static final String ISSUER = "tickets-backend";

    private final byte[] secreto;
    private final Duration ttl;
    private final Clock reloj;

    public JwtEmisorTokens(String secreto, Duration ttl, Clock reloj) {
        if (secreto == null || secreto.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("JWT_SECRET debe tener al menos 32 bytes");
        }
        this.secreto = secreto.getBytes(StandardCharsets.UTF_8);
        this.ttl = ttl;
        this.reloj = reloj;
    }

    @Override
    public Token emitir(Usuario usuario) {
        Instant ahora = reloj.instant();
        Instant expira = ahora.plus(ttl);
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .subject(String.valueOf(usuario.id()))
                .claim("rol", usuario.rol().name())
                .issueTime(Date.from(ahora))
                .expirationTime(Date.from(expira))
                .build();
        try {
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
            jwt.sign(new MACSigner(secreto));
            return new Token(jwt.serialize(), expira);
        } catch (JOSEException e) {
            throw new IllegalStateException("No se pudo firmar el JWT", e);
        }
    }

    @Override
    public Optional<Actor> validar(String token) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            if (!JWSAlgorithm.HS256.equals(jwt.getHeader().getAlgorithm()) || !jwt.verify(new MACVerifier(secreto))) {
                return Optional.empty();
            }
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            if (!ISSUER.equals(claims.getIssuer()) || claims.getExpirationTime() == null
                    || !claims.getExpirationTime().toInstant().isAfter(reloj.instant())) {
                return Optional.empty();
            }
            return Optional.of(new Actor(Long.valueOf(claims.getSubject()), Rol.valueOf(claims.getStringClaim("rol"))));
        } catch (ParseException | JOSEException | RuntimeException e) {
            return Optional.empty();
        }
    }
}
