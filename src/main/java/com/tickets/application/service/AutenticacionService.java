package com.tickets.application.service;

import com.tickets.application.port.in.AutenticarUseCase;
import com.tickets.application.port.out.EmisorTokens;
import com.tickets.application.port.out.PasswordHasher;
import com.tickets.application.port.out.SesionRepository;
import com.tickets.application.port.out.UsuarioRepository;
import com.tickets.domain.Actor;
import com.tickets.domain.Usuario;
import com.tickets.domain.exception.CredencialesInvalidasException;
import com.tickets.shared.log.Log;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;

public class AutenticacionService implements AutenticarUseCase {

    private final UsuarioRepository usuarios;
    private final SesionRepository sesiones;
    private final PasswordHasher hasher;
    private final EmisorTokens emisor;
    private final Clock reloj;
    private final Duration ttlSesion;
    private final SecureRandom random = new SecureRandom();
    /** Hash valido de una contrasena aleatoria: iguala el costo del login cuando el usuario no existe. */
    private final String hashSenuelo;

    public AutenticacionService(UsuarioRepository usuarios, SesionRepository sesiones, PasswordHasher hasher,
                                EmisorTokens emisor, Clock reloj, Duration ttlSesion) {
        this.usuarios = usuarios;
        this.sesiones = sesiones;
        this.hasher = hasher;
        this.emisor = emisor;
        this.reloj = reloj;
        this.ttlSesion = ttlSesion;
        this.hashSenuelo = hasher.hash(nuevoToken());
    }

    @Override
    public ResultadoLogin login(String correo, String password) {
        Optional<Usuario> usuario = usuarios.buscarPorCorreo(correo == null ? "" : correo.strip());
        String hash = usuario.map(Usuario::passwordHash).orElse(hashSenuelo);
        boolean coincide = hasher.coincide(password == null ? "" : password, hash);
        if (usuario.isEmpty() || !coincide) {
            Log.warn("Login fallido", Map.of("correo", correo == null ? "" : correo));
            throw new CredencialesInvalidasException();
        }

        Usuario u = usuario.get();
        EmisorTokens.Token jwt = emisor.emitir(u);
        String tokenSesion = nuevoToken();
        Instant expira = reloj.instant().plus(ttlSesion);
        sesiones.guardar(hash(tokenSesion), u.id(), expira);

        Log.info("Login exitoso", Map.of("usuarioId", u.id(), "rol", u.rol().name()));
        return new ResultadoLogin(jwt.valor(), jwt.expiraEn(), tokenSesion, expira, u.id(), u.nombre(), u.rol());
    }

    @Override
    public Optional<Actor> autenticarJwt(String jwt) {
        return jwt == null || jwt.isBlank() ? Optional.empty() : emisor.validar(jwt);
    }

    @Override
    public Optional<Actor> autenticarSesion(String tokenSesion) {
        if (tokenSesion == null || tokenSesion.isBlank()) {
            return Optional.empty();
        }
        return sesiones.buscarPorHash(hash(tokenSesion))
                .filter(s -> s.estaVigente(reloj.instant()))
                .flatMap(s -> usuarios.buscarPorId(s.usuarioId()))
                .map(Usuario::comoActor);
    }

    @Override
    public void cerrarSesion(String tokenSesion) {
        if (tokenSesion != null && !tokenSesion.isBlank()) {
            sesiones.revocar(hash(tokenSesion));
        }
    }

    private String nuevoToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hash(String token) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
