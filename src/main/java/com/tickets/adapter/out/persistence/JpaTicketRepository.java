package com.tickets.adapter.out.persistence;

import com.tickets.application.Pagina;
import com.tickets.application.port.out.FiltroTickets;
import com.tickets.application.port.out.Orden;
import com.tickets.application.port.out.TicketRepository;
import com.tickets.domain.Prioridad;
import com.tickets.domain.Ticket;
import com.tickets.domain.exception.ConflictoConcurrenciaException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@Transactional
class JpaTicketRepository implements TicketRepository {

    private final TicketJpaRepository jpa;
    private final EntityManager em;

    JpaTicketRepository(TicketJpaRepository jpa, EntityManager em) {
        this.jpa = jpa;
        this.em = em;
    }

    @Override
    public Ticket guardar(Ticket t) {
        TicketEntity e = new TicketEntity();
        e.id = t.getId();
        e.version = t.getVersion();
        e.titulo = t.getTitulo();
        e.descripcion = t.getDescripcion();
        e.categoria = t.getCategoria();
        e.prioridad = t.getPrioridad();
        e.estado = t.getEstado();
        e.solicitante = em.getReference(UsuarioEntity.class, t.getSolicitanteId());
        e.tecnicoAsignado = t.getTecnicoAsignadoId() == null ? null
                : em.getReference(UsuarioEntity.class, t.getTecnicoAsignadoId());
        e.resumenIA = t.getResumenIA();
        e.etiquetasIA = t.getEtiquetasIA();
        e.estadoAnalisis = t.getEstadoAnalisis();
        e.analizadoPor = t.getAnalizadoPor();
        e.modeloIA = t.getModeloIA();
        e.promptTemplateId = t.getPromptTemplateId();
        e.fechaCreacion = t.getFechaCreacion();
        e.fechaActualizacion = t.getFechaActualizacion();
        e.eliminadoEn = t.getEliminadoEn();
        try {
            return aDominio(jpa.saveAndFlush(e));
        } catch (ObjectOptimisticLockingFailureException ex) {
            throw new ConflictoConcurrenciaException(
                    "El ticket %d fue modificado por otro proceso; reintente".formatted(t.getId()));
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Ticket> buscarPorId(Long id) {
        return jpa.findOne(noEliminado().and((root, q, cb) -> cb.equal(root.get("id"), id)))
                .map(JpaTicketRepository::aDominio);
    }

    @Override
    @Transactional(readOnly = true)
    public Pagina<Ticket> buscar(FiltroTickets f, int pagina, int tamano) {
        Specification<TicketEntity> spec = noEliminado().and((root, q, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (f.estado() != null) ps.add(cb.equal(root.get("estado"), f.estado()));
            if (f.categoria() != null) ps.add(cb.equal(root.get("categoria"), f.categoria()));
            if (f.prioridad() != null) ps.add(cb.equal(root.get("prioridad"), f.prioridad()));
            if (f.usuarioSolicitanteId() != null) {
                ps.add(cb.equal(root.get("solicitante").get("id"), f.usuarioSolicitanteId()));
            }
            if (f.desde() != null) ps.add(cb.greaterThanOrEqualTo(root.get("fechaCreacion"), f.desde()));
            if (f.hasta() != null) ps.add(cb.lessThanOrEqualTo(root.get("fechaCreacion"), f.hasta()));
            if (f.tieneTexto()) {
                // la coleccion utf8mb4_0900_ai_ci ya ignora mayusculas y tildes
                String patron = "%" + escaparLike(f.texto().strip()) + "%";
                ps.add(cb.or(cb.like(root.get("titulo"), patron, '\\'), cb.like(root.get("descripcion"), patron, '\\')));
            }
            if (!esConsultaDeConteo(q)) {
                q.orderBy(ordenar(f.orden() == null ? Orden.POR_DEFECTO : f.orden(), root, cb));
            }
            return cb.and(ps.toArray(new Predicate[0]));
        });
        Page<TicketEntity> page = jpa.findAll(spec, PageRequest.of(pagina, tamano));
        return new Pagina<>(page.getContent().stream().map(JpaTicketRepository::aDominio).toList(),
                pagina, tamano, page.getTotalElements());
    }

    @Override
    @Transactional(readOnly = true)
    public List<Ticket> buscarPorIds(List<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<Long, Ticket> porId = jpa.findAll(noEliminado().and((root, q, cb) -> root.get("id").in(ids))).stream()
                .map(JpaTicketRepository::aDominio).collect(Collectors.toMap(Ticket::getId, Function.identity()));
        return ids.stream().map(porId::get).filter(Objects::nonNull).toList();
    }

    private static boolean esConsultaDeConteo(CriteriaQuery<?> q) {
        return Long.class.equals(q.getResultType()) || long.class.equals(q.getResultType());
    }

    /** Siempre desempata por id descendente para que la paginacion sea estable. */
    private static List<Order> ordenar(Orden orden, Root<TicketEntity> root, CriteriaBuilder cb) {
        Expression<?> expresion = switch (orden.campo()) {
            case ID -> root.get("id");
            case FECHA_CREACION -> root.get("fechaCreacion");
            case FECHA_ACTUALIZACION -> root.get("fechaActualizacion");
            case TITULO -> root.get("titulo");
            case PRIORIDAD -> gravedad(root, cb);
        };
        Order principal = orden.direccion() == Orden.Direccion.ASC ? cb.asc(expresion) : cb.desc(expresion);
        return orden.campo() == Orden.Campo.ID ? List.of(principal) : List.of(principal, cb.desc(root.get("id")));
    }

    /** BAJA=1 < MEDIA=2 < ALTA=3 < CRITICA=4; sin prioridad (analisis pendiente) = 0. */
    private static Expression<Integer> gravedad(Root<TicketEntity> root, CriteriaBuilder cb) {
        return cb.<Integer>selectCase()
                .when(cb.equal(root.get("prioridad"), Prioridad.BAJA), 1)
                .when(cb.equal(root.get("prioridad"), Prioridad.MEDIA), 2)
                .when(cb.equal(root.get("prioridad"), Prioridad.ALTA), 3)
                .when(cb.equal(root.get("prioridad"), Prioridad.CRITICA), 4)
                .otherwise(0);
    }

    private static String escaparLike(String texto) {
        return texto.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static Specification<TicketEntity> noEliminado() {
        return (root, q, cb) -> cb.isNull(root.get("eliminadoEn"));
    }

    private static Ticket aDominio(TicketEntity e) {
        return Ticket.reconstruir(e.id, e.version, e.titulo, e.descripcion, e.categoria, e.prioridad, e.estado,
                e.solicitante.getId(), e.tecnicoAsignado == null ? null : e.tecnicoAsignado.getId(), e.resumenIA,
                e.etiquetasIA, e.estadoAnalisis, e.analizadoPor, e.modeloIA, e.promptTemplateId,
                e.fechaCreacion, e.fechaActualizacion, e.eliminadoEn);
    }
}
