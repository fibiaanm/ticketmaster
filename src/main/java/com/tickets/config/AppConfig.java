package com.tickets.config;

import com.tickets.adapter.in.web.RequestLoggingFilter;
import com.tickets.adapter.in.worker.TareasWorker;
import com.tickets.adapter.out.analysis.AsyncDisparadorAnalisis;
import com.tickets.adapter.out.analysis.IndexacionDesactivada;
import com.tickets.adapter.out.analysis.IndexacionPorCola;
import com.tickets.adapter.out.analysis.SyncDisparadorAnalisis;
import com.tickets.application.port.in.AnalizarTicketUseCase;
import com.tickets.application.port.in.ManejadorTarea;
import com.tickets.application.port.out.*;
import com.tickets.application.service.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;

/** Casos de uso, cola de tareas, estrategia de analisis y observabilidad. */
@Configuration
@EnableScheduling
@EnableConfigurationProperties({AnalysisProperties.class})
public class AppConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    Transaccion transaccion(EjecutorTransaccional ejecutor) {
        return new Transaccion(ejecutor);
    }

    @Bean
    TicketService ticketService(TicketRepository tickets, BuscadorTickets buscador, UsuarioRepository usuarios,
                                DisparadorAnalisis disparador, ProgramadorIndexacion indexacion,
                                RegistroEventos eventos, Transaccion transaccion, Clock clock) {
        return new TicketService(tickets, buscador, usuarios, disparador, indexacion, eventos, transaccion, clock);
    }

    @Bean
    MetricasService metricasService(MetricasRepository metricas) {
        return new MetricasService(metricas);
    }

    @Bean
    HistorialService historialService(TicketRepository tickets, RegistroEventos eventos) {
        return new HistorialService(tickets, eventos);
    }

    @Bean
    AnalizarTicketService analizarTicketService(TicketRepository tickets, AnalizadorTicketService analizador,
                                                Transaccion transaccion, ProgramadorIndexacion indexacion,
                                                RegistroEventos eventos, Clock clock) {
        return new AnalizarTicketService(tickets, analizador, transaccion, indexacion, eventos, clock);
    }

    @Bean
    VistaTickets vistaTickets(UsuarioRepository usuarios) {
        return new VistaTickets(usuarios);
    }

    @Bean
    SincronizarPromptsService sincronizarPromptsService(PromptSource source, PromptTemplateRepository repo) {
        return new SincronizarPromptsService(source, repo);
    }

    @Bean
    CommandLineRunner cargarPrompts(SincronizarPromptsService servicio) {
        return args -> servicio.sincronizar();
    }

    // ---- cola de tareas y worker
    @Bean
    ProcesarColaService procesarColaService(ColaTareas cola, AnalizarTicketUseCase analizar, Clock clock,
                                            AnalysisProperties props,
                                            ObjectProvider<IndexarTicketService> indexar) {
        Map<TipoTarea, ManejadorTarea> manejadores = new EnumMap<>(TipoTarea.class);
        manejadores.put(TipoTarea.ANALIZAR, analizar::analizar);
        indexar.ifAvailable(servicio -> manejadores.put(TipoTarea.INDEXAR, servicio::manejar));
        return new ProcesarColaService(cola, manejadores, clock, props.maxAttempts(),
                Duration.ofSeconds(props.retryDelaySeconds()));
    }

    @Bean
    TareasWorker tareasWorker(ProcesarColaService procesar) {
        return new TareasWorker(procesar);
    }

    // ---- estrategia de analisis (ANALYSIS_MODE)
    @Bean
    @ConditionalOnProperty(name = "analysis.mode", havingValue = "sync")
    DisparadorAnalisis disparadorSync(AnalizarTicketUseCase analizar) {
        return new SyncDisparadorAnalisis(analizar);
    }

    @Bean
    @ConditionalOnProperty(name = "analysis.mode", havingValue = "async", matchIfMissing = true)
    DisparadorAnalisis disparadorAsync(ColaTareas cola) {
        return new AsyncDisparadorAnalisis(cola);
    }

    @Bean
    ProgramadorIndexacion programadorIndexacion(ColaTareas cola, @Value("${search.engine:mysql}") String motor) {
        return "mysql".equalsIgnoreCase(motor) ? new IndexacionDesactivada() : new IndexacionPorCola(cola);
    }

    // ---- observabilidad
    @Bean
    FilterRegistrationBean<RequestLoggingFilter> requestLoggingFilter() {
        var registro = new FilterRegistrationBean<>(new RequestLoggingFilter());
        registro.setOrder(Ordered.HIGHEST_PRECEDENCE + 10); // antes de Spring Security: registra tambien los 401
        return registro;
    }
}
