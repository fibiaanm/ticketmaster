package com.tickets.application.service;

import com.tickets.application.Pagina;
import com.tickets.application.port.out.UsuarioRepository;
import com.tickets.domain.Ticket;
import com.tickets.domain.Usuario;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Enriquece tickets con sus usuarios en una sola consulta (evita N+1 en los listados). */
public class VistaTickets {

    private final UsuarioRepository usuarios;

    public VistaTickets(UsuarioRepository usuarios) {
        this.usuarios = usuarios;
    }

    public TicketVista de(Ticket ticket) {
        return de(List.of(ticket)).get(0);
    }

    public List<TicketVista> de(List<Ticket> tickets) {
        Set<Long> ids = new HashSet<>();
        tickets.forEach(t -> {
            ids.add(t.getSolicitanteId());
            if (t.getTecnicoAsignadoId() != null) {
                ids.add(t.getTecnicoAsignadoId());
            }
        });
        Map<Long, Usuario> porId = usuarios.buscarPorIds(ids).stream()
                .collect(Collectors.toMap(Usuario::id, Function.identity()));
        return tickets.stream().map(t -> new TicketVista(t, porId.get(t.getSolicitanteId()),
                t.getTecnicoAsignadoId() == null ? null : porId.get(t.getTecnicoAsignadoId()))).toList();
    }

    public Pagina<TicketVista> de(Pagina<Ticket> pagina) {
        return new Pagina<>(de(pagina.contenido()), pagina.pagina(), pagina.tamano(), pagina.totalElementos());
    }
}
