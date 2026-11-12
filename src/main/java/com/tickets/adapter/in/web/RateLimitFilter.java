package com.tickets.adapter.in.web;

import com.tickets.application.service.Limitador;
import com.tickets.application.service.Limitador.Decision;
import com.tickets.config.RateLimitProperties;
import com.tickets.domain.Actor;
import com.tickets.security.AutenticacionFilter;
import com.tickets.shared.log.Log;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Limita la tasa de peticiones a /api/**. Va DESPUES de la autenticacion para poder limitar por usuario.
 * Reglas: login por IP (fuerza bruta), alta de tickets y reanalisis por usuario (cada uno puede costar una
 * llamada al modelo de IA) y un limite general. Responde 429 con Retry-After.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private static final JsonMapper JSON = JsonMapper.builder().findAndAddModules().build();

    private record Regla(String nombre, Predicate<HttpServletRequest> aplica, boolean soloIp, int limite) {
    }

    private final Limitador limitador;
    private final Duration ventana;
    private final List<Regla> reglas = new ArrayList<>();

    public RateLimitFilter(Limitador limitador, RateLimitProperties props) {
        this.limitador = limitador;
        this.ventana = props.window();
        reglas.add(new Regla("login", r -> esPost(r) && r.getRequestURI().equals("/api/auth/login"), true, props.login()));
        reglas.add(new Regla("crear-ticket", r -> esPost(r) && r.getRequestURI().equals("/api/tickets"), false,
                props.crearTicket()));
        reglas.add(new Regla("reanalizar", r -> esPost(r) && r.getRequestURI().matches("/api/tickets/\\d+/reanalizar"),
                false, props.reanalizar()));
        reglas.add(new Regla("general", r -> true, false, props.general()));
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        List<Decision> decisiones = new ArrayList<>();
        for (Regla regla : reglas) {
            if (!regla.aplica().test(request)) {
                continue;
            }
            Decision d = limitador.evaluar(clave(regla, request), regla.limite(), ventana);
            decisiones.add(d);
            if (!d.permitido()) {
                rechazar(request, response, regla, d);
                return;
            }
        }
        decisiones.stream().min(Comparator.comparingLong(Decision::restantes)).ifPresent(d -> {
            response.setHeader("X-RateLimit-Limit", String.valueOf(d.limite()));
            response.setHeader("X-RateLimit-Remaining", String.valueOf(d.restantes()));
        });
        chain.doFilter(request, response);
    }

    private static String clave(Regla regla, HttpServletRequest request) {
        if (!regla.soloIp() && request.getAttribute(AutenticacionFilter.ATRIBUTO_ACTOR) instanceof Actor a) {
            return regla.nombre() + ":u" + a.id();
        }
        // sin proxy de confianza configurado, la unica IP fiable es la del socket
        return regla.nombre() + ":ip" + request.getRemoteAddr();
    }

    private void rechazar(HttpServletRequest request, HttpServletResponse response, Regla regla, Decision d)
            throws IOException {
        Log.warn("Rate limit excedido", Map.of("regla", regla.nombre(), "path", request.getRequestURI()));
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", String.valueOf(d.reintentarEnSegundos()));
        response.setHeader("X-RateLimit-Limit", String.valueOf(d.limite()));
        response.setHeader("X-RateLimit-Remaining", "0");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(JSON.writeValueAsString(GlobalExceptionHandler.nuevo(HttpStatus.TOO_MANY_REQUESTS,
                "Demasiadas peticiones; reintente en %d segundos".formatted(d.reintentarEnSegundos()),
                request.getRequestURI())));
    }

    private static boolean esPost(HttpServletRequest r) {
        return "POST".equals(r.getMethod());
    }
}
