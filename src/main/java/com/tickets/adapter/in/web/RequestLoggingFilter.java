package com.tickets.adapter.in.web;

import com.tickets.domain.Actor;
import com.tickets.security.AutenticacionFilter;
import com.tickets.shared.log.Contexto;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Log de acceso de red (GET/POST/PUT/PATCH/DELETE): metodo, ruta, status, duracion, usuario y rol.
 * Asigna el requestId (correlacion). Nunca registra cabeceras, cuerpos ni query strings.
 */
public class RequestLoggingFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";
    private static final Pattern ID_VALIDO = Pattern.compile("^[A-Za-z0-9-]{8,64}$");
    private static final Logger ACCESS = LoggerFactory.getLogger("com.tickets.access");

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String ruta = request.getRequestURI();
        return ruta.startsWith("/actuator") || ruta.startsWith("/assets/") || ruta.equals("/")
                || ruta.equals("/index.html") || ruta.equals("/favicon.ico");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String entrante = request.getHeader(HEADER);
        String requestId = entrante != null && ID_VALIDO.matcher(entrante).matches() ? entrante : UUID.randomUUID().toString();
        Contexto.requestId(requestId);
        response.setHeader(HEADER, requestId);
        long inicio = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } finally {
            int status = response.getStatus();
            MDC.put("severity", status >= 500 ? "ERROR" : status >= 400 ? "WARN" : "INFO");
            MDC.put("http_method", request.getMethod());
            MDC.put("http_path", request.getRequestURI());
            MDC.put("http_status", String.valueOf(status));
            MDC.put("duration_ms", String.valueOf((System.nanoTime() - inicio) / 1_000_000));
            if (request.getAttribute(AutenticacionFilter.ATRIBUTO_ACTOR) instanceof Actor a) {
                MDC.put("user_id", String.valueOf(a.id()));
                MDC.put("user_rol", a.rol().name());
            }
            String mensaje = "%s %s -> %d".formatted(request.getMethod(), request.getRequestURI(), status);
            if (status >= 500) ACCESS.error(mensaje);
            else if (status >= 400) ACCESS.warn(mensaje);
            else ACCESS.info(mensaje);
            for (String k : new String[]{"severity", "http_method", "http_path", "http_status", "duration_ms",
                    "user_id", "user_rol"}) {
                MDC.remove(k);
            }
            Contexto.limpiar();
        }
    }
}
