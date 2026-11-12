package com.tickets.security;

import com.tickets.adapter.in.web.GlobalExceptionHandler;
import com.tickets.adapter.in.web.RateLimitFilter;
import com.tickets.application.service.Limitador;
import com.tickets.config.RateLimitProperties;
import com.tickets.application.port.in.AutenticarUseCase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.DelegatingRequestMatcherHeaderWriter;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;

@Configuration
public class SecurityConfig {

    /** Solo la pagina y sus recursos: Swagger UI necesita scripts propios y no debe heredar esta politica. */
    private static final RequestMatcher PAGINA_ESTATICA = new OrRequestMatcher(
            PathPatternRequestMatcher.withDefaults().matcher("/"),
            PathPatternRequestMatcher.withDefaults().matcher("/index.html"),
            PathPatternRequestMatcher.withDefaults().matcher("/assets/**"));

    private static final String CSP = "default-src 'none'; script-src 'self'; style-src 'self'; "
            + "img-src 'self' data:; connect-src 'self'; font-src 'self'; base-uri 'none'; "
            + "form-action 'self'; frame-ancestors 'none'";

    private static final JsonMapper MAPPER = JsonMapper.builder().findAndAddModules().build();

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, AutenticarUseCase autenticar, Limitador limitador,
                                    RateLimitProperties rateLimit) throws Exception {
        http
                // API sin estado con credenciales en cabeceras (sin cookies): CSRF no aplica.
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .headers(h -> h.addHeaderWriter(new DelegatingRequestMatcherHeaderWriter(
                        PAGINA_ESTATICA, new StaticHeadersWriter("Content-Security-Policy", CSP))))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/", "/index.html", "/favicon.ico", "/assets/**").permitAll()
                        .requestMatchers("/api/auth/login", "/actuator/health", "/actuator/health/**",
                                "/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) -> escribir(res, HttpStatus.UNAUTHORIZED,
                                "Autenticación requerida o credenciales inválidas", req.getRequestURI()))
                        .accessDeniedHandler((req, res, ex) -> escribir(res, HttpStatus.FORBIDDEN,
                                "No tiene permisos para realizar esta acción", req.getRequestURI())))
                .addFilterBefore(new AutenticacionFilter(autenticar), UsernamePasswordAuthenticationFilter.class);
        if (rateLimit.enabled()) {
            // despues de autenticar: permite limitar por usuario ademas de por IP
            http.addFilterAfter(new RateLimitFilter(limitador, rateLimit), AutenticacionFilter.class);
        }
        return http.build();
    }

    /** Evita el usuario en memoria con contraseña generada de Spring Boot: no se usa autenticacion por formulario. */
    @Bean
    AuthenticationManager authenticationManager() {
        return authentication -> {
            throw new AuthenticationServiceException("Autenticación por usuario/contraseña de Spring no habilitada");
        };
    }

    private static void escribir(jakarta.servlet.http.HttpServletResponse res, HttpStatus status, String mensaje,
                                 String path) throws IOException {
        res.setStatus(status.value());
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.setCharacterEncoding("UTF-8");
        res.getWriter().write(MAPPER.writeValueAsString(GlobalExceptionHandler.nuevo(status, mensaje, path)));
    }
}
