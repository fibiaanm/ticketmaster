package com.tickets.application.service;

import com.tickets.application.port.in.AnalizarTicketUseCase;
import com.tickets.application.port.out.AnalisisTicketResponse;
import com.tickets.application.port.out.AnalizadorTicketService;
import com.tickets.application.port.out.ProgramadorIndexacion;
import com.tickets.application.port.out.RegistroEventos;
import com.tickets.application.port.out.TicketRepository;
import com.tickets.domain.Categoria;
import com.tickets.domain.EnumParser;
import com.tickets.domain.EventoTicket;
import com.tickets.domain.TipoEvento;
import com.tickets.shared.log.Contexto;
import com.tickets.domain.OrigenAnalisis;
import com.tickets.domain.Prioridad;
import com.tickets.domain.Ticket;
import com.tickets.shared.log.Log;

import java.time.Clock;
import java.util.Map;
import java.util.Optional;

/**
 * El analisis con IA puede tardar segundos. Por eso NO se mantiene el ticket cargado durante la llamada:
 * se marca ANALIZANDO, se llama al modelo, y al terminar se vuelve a leer el ticket y se aplica SOLO el
 * resultado del analisis sobre los datos frescos (otro usuario pudo cambiar estado o tecnico mientras tanto).
 */
public class AnalizarTicketService implements AnalizarTicketUseCase {

    private final TicketRepository tickets;
    private final AnalizadorTicketService analizador;
    private final Transaccion transaccion;
    private final ProgramadorIndexacion indexacion;
    private final RegistroEventos eventos;
    private final Clock reloj;

    public AnalizarTicketService(TicketRepository tickets, AnalizadorTicketService analizador,
                                 Transaccion transaccion, ProgramadorIndexacion indexacion,
                                 RegistroEventos eventos, Clock reloj) {
        this.tickets = tickets;
        this.analizador = analizador;
        this.transaccion = transaccion;
        this.indexacion = indexacion;
        this.eventos = eventos;
        this.reloj = reloj;
    }

    @Override
    public void analizar(Long ticketId) {
        Optional<Ticket> iniciado = transaccion.conReintento(() -> tickets.buscarPorId(ticketId).map(t -> {
            t.iniciarAnalisis(reloj.instant());
            return persistir(t);
        }));
        if (iniciado.isEmpty()) {
            Log.warn("Analisis omitido: el ticket no existe o fue eliminado", Map.of("ticketId", ticketId));
            return;
        }
        Ticket ticket = iniciado.get();

        AnalisisTicketResponse r;
        try {
            r = analizador.analizar(ticket.getTitulo(), ticket.getDescripcion());
            Categoria categoria = EnumParser.parsear(Categoria.class, r.categoria(), "Categoría inválida");
            Prioridad prioridad = EnumParser.parsear(Prioridad.class, r.prioridad(), "Prioridad inválida");
            OrigenAnalisis origen = r.origen() == null ? OrigenAnalisis.REGLAS : r.origen();
            transaccion.conReintento(() -> tickets.buscarPorId(ticketId).map(fresco -> {
                fresco.completarAnalisis(categoria, prioridad, r.resumen(), r.etiquetas(), origen, r.modelo(),
                        r.promptTemplateId(), reloj.instant());
                Ticket guardado = persistir(fresco);
                Map<String, Object> detalle = new java.util.LinkedHashMap<>();
                detalle.put("categoria", categoria.name());
                detalle.put("prioridad", prioridad.name());
                detalle.put("analizadoPor", origen.name());
                detalle.put("modelo", r.modelo());
                detalle.put("promptTemplateId", r.promptTemplateId());
                registrar(guardado, TipoEvento.ANALISIS_COMPLETADO, detalle);
                return guardado;
            }).orElse(null));
            Log.info("Ticket analizado", Map.of("ticketId", ticketId, "categoria", categoria.name(),
                    "prioridad", prioridad.name(), "origen", origen.name(), "modelo", String.valueOf(r.modelo())));
        } catch (RuntimeException e) {
            transaccion.conReintento(() -> tickets.buscarPorId(ticketId).map(fresco -> {
                fresco.fallarAnalisis(reloj.instant());
                Ticket guardado = persistir(fresco);
                registrar(guardado, TipoEvento.ANALISIS_FALLIDO,
                        Map.of("error", e.getClass().getSimpleName() + ": " + e.getMessage()));
                return guardado;
            }).orElse(null));
            throw e;
        }
    }

    private void registrar(Ticket ticket, TipoEvento tipo, Map<String, Object> detalle) {
        // actor null: lo hace el sistema
        eventos.registrar(EventoTicket.nuevo(ticket.getId(), tipo, null, Contexto.requestId(), detalle, reloj.instant()));
    }

    private Ticket persistir(Ticket ticket) {
        Ticket guardado = tickets.guardar(ticket);
        indexacion.programar(guardado.getId());
        return guardado;
    }
}
