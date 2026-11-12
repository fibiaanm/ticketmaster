package com.tickets.adapter.out.persistence;

import com.tickets.application.Pagina;
import com.tickets.application.port.out.RegistroEventos;
import com.tickets.domain.Actor;
import com.tickets.domain.EventoTicket;
import com.tickets.domain.TipoEvento;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@Transactional
class JpaRegistroEventos implements RegistroEventos {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final TypeReference<Map<String, Object>> MAPA = new TypeReference<>() { };

    private final EventoJpaRepository jpa;

    JpaRegistroEventos(EventoJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public void registrar(EventoTicket ev) {
        EventoEntity e = new EventoEntity();
        e.ticketId = ev.ticketId();
        e.tipo = ev.tipo();
        e.actorId = ev.actor() == null ? null : ev.actor().id();
        e.actorRol = ev.actor() == null ? null : ev.actor().rol();
        e.requestId = ev.requestId();
        e.detalle = ev.detalle().isEmpty() ? null : JSON.writeValueAsString(ev.detalle());
        e.fecha = ev.fecha();
        jpa.save(e);
    }

    @Override
    @Transactional(readOnly = true)
    public List<EventoTicket> deTicket(Long ticketId, List<TipoEvento> tipos) {
        Specification<EventoEntity> spec = (root, q, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            ps.add(cb.equal(root.get("ticketId"), ticketId));
            if (!tipos.isEmpty()) {
                ps.add(root.get("tipo").in(tipos));
            }
            return cb.and(ps.toArray(new Predicate[0]));
        };
        return jpa.findAll(spec, Sort.by("id")).stream().map(JpaRegistroEventos::aDominio).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Pagina<EventoTicket> auditoria(FiltroAuditoria f, int pagina, int tamano) {
        Specification<EventoEntity> spec = (root, q, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (f.ticketId() != null) ps.add(cb.equal(root.get("ticketId"), f.ticketId()));
            if (f.tipo() != null) ps.add(cb.equal(root.get("tipo"), f.tipo()));
            if (f.actorId() != null) ps.add(cb.equal(root.get("actorId"), f.actorId()));
            if (f.desde() != null) ps.add(cb.greaterThanOrEqualTo(root.get("fecha"), f.desde()));
            if (f.hasta() != null) ps.add(cb.lessThanOrEqualTo(root.get("fecha"), f.hasta()));
            return cb.and(ps.toArray(new Predicate[0]));
        };
        Page<EventoEntity> page = jpa.findAll(spec, PageRequest.of(pagina, tamano, Sort.by(Sort.Direction.DESC, "id")));
        return new Pagina<>(page.getContent().stream().map(JpaRegistroEventos::aDominio).toList(),
                pagina, tamano, page.getTotalElements());
    }

    private static EventoTicket aDominio(EventoEntity e) {
        Actor actor = e.actorId == null ? null : new Actor(e.actorId, e.actorRol);
        Map<String, Object> detalle = e.detalle == null ? Map.of() : JSON.readValue(e.detalle, MAPA);
        return new EventoTicket(e.id, e.ticketId, e.tipo, actor, e.requestId, detalle, e.fecha);
    }
}
