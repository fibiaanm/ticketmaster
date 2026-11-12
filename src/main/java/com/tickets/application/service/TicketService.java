package com.tickets.application.service;

import com.tickets.application.Pagina;
import com.tickets.application.port.in.AsignarTecnicoUseCase;
import com.tickets.application.port.in.CambiarEstadoUseCase;
import com.tickets.application.port.in.ConsultarTicketsUseCase;
import com.tickets.application.port.in.CrearTicketUseCase;
import com.tickets.application.port.in.EliminarTicketUseCase;
import com.tickets.application.port.out.BuscadorTickets;
import com.tickets.application.port.out.DisparadorAnalisis;
import com.tickets.application.port.out.FiltroTickets;
import com.tickets.application.port.in.ReanalizarTicketUseCase;
import com.tickets.application.port.out.ProgramadorIndexacion;
import com.tickets.application.port.out.RegistroEventos;
import com.tickets.application.port.out.TicketRepository;
import com.tickets.application.port.out.UsuarioRepository;
import com.tickets.domain.Actor;
import com.tickets.domain.EnumParser;
import com.tickets.domain.EstadoTicket;
import com.tickets.domain.EventoTicket;
import com.tickets.domain.TipoEvento;
import com.tickets.shared.log.Contexto;
import com.tickets.domain.Rol;
import com.tickets.domain.Ticket;
import com.tickets.domain.Usuario;
import com.tickets.domain.exception.AccesoDenegadoException;
import com.tickets.domain.exception.RecursoNoEncontradoException;
import com.tickets.shared.log.Log;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;

public class TicketService implements CrearTicketUseCase, ConsultarTicketsUseCase, CambiarEstadoUseCase,
        AsignarTecnicoUseCase, EliminarTicketUseCase, ReanalizarTicketUseCase {

    private final TicketRepository tickets;
    private final BuscadorTickets buscador;
    private final UsuarioRepository usuarios;
    private final DisparadorAnalisis disparador;
    private final ProgramadorIndexacion indexacion;
    private final RegistroEventos eventos;
    private final Transaccion transaccion;
    private final Clock reloj;

    public TicketService(TicketRepository tickets, BuscadorTickets buscador, UsuarioRepository usuarios, DisparadorAnalisis disparador,
                         ProgramadorIndexacion indexacion, RegistroEventos eventos, Transaccion transaccion,
                         Clock reloj) {
        this.tickets = tickets;
        this.buscador = buscador;
        this.usuarios = usuarios;
        this.disparador = disparador;
        this.indexacion = indexacion;
        this.eventos = eventos;
        this.transaccion = transaccion;
        this.reloj = reloj;
    }

    @Override
    public Ticket crear(Actor actor, Comando comando) {
        if (actor.rol() == Rol.USUARIO && !actor.id().equals(comando.usuarioId())) {
            throw new AccesoDenegadoException("No puede crear tickets en nombre de otro usuario");
        }
        usuarios.buscarPorId(comando.usuarioId())
                .orElseThrow(() -> RecursoNoEncontradoException.usuario(comando.usuarioId()));

        // ticket + tarea de analisis + tarea de indexacion: todo o nada
        Ticket ticket = transaccion.conReintento(() -> {
            Ticket guardado = tickets.guardar(Ticket.nuevo(comando.titulo(), comando.descripcion(),
                    comando.usuarioId(), ahora()));
            registrarEvento(guardado, TipoEvento.TICKET_CREADO, actor,
                    Map.of("titulo", guardado.getTitulo(), "solicitanteId", guardado.getSolicitanteId()));
            disparador.registrar(guardado);
            indexacion.programar(guardado.getId());
            return guardado;
        });
        Log.info("Ticket creado", Map.of("ticketId", ticket.getId(), "solicitanteId", ticket.getSolicitanteId()));

        try {
            disparador.ejecutarTrasCommit(ticket);
        } catch (RuntimeException e) {
            // el ticket ya esta confirmado: no se pierde la solicitud por un fallo del analisis
            Log.error("No se pudo ejecutar el analisis del ticket", Map.of("ticketId", ticket.getId()), e);
            marcarAnalisisFallido(ticket.getId());
        }
        return tickets.buscarPorId(ticket.getId()).orElse(ticket);
    }

    @Override
    public Pagina<Ticket> listar(Actor actor, FiltroTickets filtro, int pagina, int tamano) {
        FiltroTickets efectivo = actor.rol() == Rol.USUARIO ? filtro.conSolicitante(actor.id()) : filtro;
        return buscador.buscar(efectivo, pagina, tamano);
    }

    @Override
    public Ticket obtener(Actor actor, Long id) {
        Ticket ticket = tickets.buscarPorId(id).orElseThrow(() -> RecursoNoEncontradoException.ticket(id));
        if (!ticket.esVisiblePara(actor)) {
            // 404 (no 403) para no revelar la existencia de tickets ajenos
            throw RecursoNoEncontradoException.ticket(id);
        }
        return ticket;
    }

    @Override
    public Ticket cambiarEstado(Actor actor, Long ticketId, String estado) {
        exigirRol(actor, "cambiar el estado de un ticket", Rol.SOPORTE, Rol.ADMIN);
        EstadoTicket nuevo = EnumParser.parsear(EstadoTicket.class, estado, "Estado inválido");
        Ticket guardado = transaccion.conReintento(() -> {
            Ticket ticket = cargar(ticketId);
            EstadoTicket anterior = ticket.getEstado();
            ticket.cambiarEstado(nuevo, ahora());
            Ticket actualizado = persistir(ticket);
            registrarEvento(actualizado, TipoEvento.ESTADO_CAMBIADO, actor, Map.of("de", anterior.name(), "a", nuevo.name()));
            return actualizado;
        });
        Log.info("Estado de ticket actualizado",
                Map.of("ticketId", ticketId, "a", nuevo.name(), "actorId", actor.id()));
        return guardado;
    }

    @Override
    public Ticket asignar(Actor actor, Long ticketId, Long tecnicoId) {
        exigirRol(actor, "asignar técnicos", Rol.ADMIN);
        Usuario tecnico = usuarios.buscarPorId(tecnicoId).orElseThrow(() -> RecursoNoEncontradoException.usuario(tecnicoId));
        Ticket guardado = transaccion.conReintento(() -> {
            Ticket ticket = cargar(ticketId);
            Long anterior = ticket.getTecnicoAsignadoId();
            ticket.asignarTecnico(tecnico, ahora());
            Ticket actualizado = persistir(ticket);
            Map<String, Object> detalle = new java.util.LinkedHashMap<>();
            detalle.put("anterior", anterior);
            detalle.put("nuevo", tecnicoId);
            registrarEvento(actualizado, TipoEvento.TECNICO_ASIGNADO, actor, detalle);
            return actualizado;
        });
        Log.info("Técnico asignado", Map.of("ticketId", ticketId, "tecnicoId", tecnicoId, "actorId", actor.id()));
        return guardado;
    }

    @Override
    public void eliminar(Actor actor, Long ticketId) {
        exigirRol(actor, "eliminar tickets", Rol.ADMIN);
        transaccion.conReintento(() -> {
            Ticket ticket = cargar(ticketId);
            ticket.eliminar(ahora());
            Ticket guardado = persistir(ticket);
            registrarEvento(guardado, TipoEvento.TICKET_ELIMINADO, actor, Map.of());
            return guardado;
        });
        Log.warn("Ticket eliminado (borrado lógico)", Map.of("ticketId", ticketId, "actorId", actor.id()));
    }

    private Ticket cargar(Long ticketId) {
        return tickets.buscarPorId(ticketId).orElseThrow(() -> RecursoNoEncontradoException.ticket(ticketId));
    }

    private Ticket persistir(Ticket ticket) {
        Ticket guardado = tickets.guardar(ticket);
        indexacion.programar(guardado.getId());
        return guardado;
    }

    private void marcarAnalisisFallido(Long ticketId) {
        transaccion.conReintento(() -> tickets.buscarPorId(ticketId).map(t -> {
            t.fallarAnalisis(ahora());
            Ticket guardado = persistir(t);
            registrarEvento(guardado, TipoEvento.ANALISIS_FALLIDO, null, Map.of("causa", "no se pudo ejecutar"));
            return guardado;
        }).orElse(null));
    }

    @Override
    public Ticket reanalizar(Actor actor, Long ticketId) {
        exigirRol(actor, "solicitar un nuevo análisis", Rol.ADMIN);
        Ticket ticket = transaccion.conReintento(() -> {
            Ticket t = cargar(ticketId);
            t.solicitarReanalisis(ahora());
            Ticket guardado = persistir(t);
            registrarEvento(guardado, TipoEvento.REANALISIS_SOLICITADO, actor, Map.of());
            disparador.registrar(guardado);
            return guardado;
        });
        Log.info("Reanálisis solicitado", Map.of("ticketId", ticketId, "actorId", actor.id()));
        try {
            disparador.ejecutarTrasCommit(ticket);
        } catch (RuntimeException e) {
            Log.error("No se pudo ejecutar el reanálisis", Map.of("ticketId", ticketId), e);
            marcarAnalisisFallido(ticketId);
        }
        return tickets.buscarPorId(ticketId).orElse(ticket);
    }

    private void registrarEvento(Ticket ticket, TipoEvento tipo, Actor actor, Map<String, Object> detalle) {
        eventos.registrar(EventoTicket.nuevo(ticket.getId(), tipo, actor, Contexto.requestId(), detalle, ahora()));
    }

    private static void exigirRol(Actor actor, String accion, Rol... permitidos) {
        for (Rol r : permitidos) {
            if (actor.rol() == r) {
                return;
            }
        }
        throw new AccesoDenegadoException("Su rol no permite " + accion);
    }

    private Instant ahora() {
        return reloj.instant();
    }
}
