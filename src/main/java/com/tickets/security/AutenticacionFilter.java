package com.tickets.security;

import com.tickets.application.port.in.AutenticarUseCase;
import com.tickets.domain.Actor;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Acepta dos credenciales equivalentes: "Authorization: Bearer <jwt>" o "X-Ticket-Auth: <sesion>".
 * Ambas producen el mismo Actor (id + rol). Si se envia una credencial invalida, la peticion queda
 * sin autenticar (-> 401); no se intenta con la otra cabecera.
 */
public class AutenticacionFilter extends OncePerRequestFilter {

    public static final String HEADER_SESION = "X-Ticket-Auth";
    public static final String ATRIBUTO_ACTOR = "ticket.actor";

    private final AutenticarUseCase autenticar;

    public AutenticacionFilter(AutenticarUseCase autenticar) {
        this.autenticar = autenticar;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Optional<Actor> actor = Optional.empty();
        String authorization = request.getHeader("Authorization");
        String sesion = request.getHeader(HEADER_SESION);

        if (authorization != null && authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            actor = autenticar.autenticarJwt(authorization.substring(7).strip());
        } else if (sesion != null) {
            actor = autenticar.autenticarSesion(sesion.strip());
        }

        actor.ifPresent(a -> {
            var auth = new UsernamePasswordAuthenticationToken(a, null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + a.rol().name())));
            SecurityContextHolder.getContext().setAuthentication(auth);
            request.setAttribute(ATRIBUTO_ACTOR, a);
        });
        chain.doFilter(request, response);
    }
}
