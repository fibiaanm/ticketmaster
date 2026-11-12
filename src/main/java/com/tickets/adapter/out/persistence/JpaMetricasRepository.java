package com.tickets.adapter.out.persistence;

import com.tickets.application.port.out.MetricasRepository;
import com.tickets.application.port.out.MetricasTickets;
import com.tickets.application.port.out.MetricasTickets.Celda;
import com.tickets.domain.Categoria;
import com.tickets.domain.EstadoAnalisis;
import com.tickets.domain.EstadoTicket;
import com.tickets.domain.Prioridad;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Metricas con GROUP BY en MySQL (usa los indices de categoria/estado). */
@Component("metricasRepositoryJpa")
@Transactional(readOnly = true)
class JpaMetricasRepository implements MetricasRepository {

    private final EntityManager em;

    JpaMetricasRepository(EntityManager em) {
        this.em = em;
    }

    @Override
    public MetricasTickets calcular() {
        Map<String, Long> porCategoria = ceros(Categoria.values(), true);
        Map<String, Long> porPrioridad = ceros(Prioridad.values(), true);
        List<Celda> matriz = new ArrayList<>();
        long total = 0;

        for (Object[] fila : em.createQuery("""
                SELECT t.categoria, t.prioridad, COUNT(t) FROM TicketEntity t
                 WHERE t.eliminadoEn IS NULL GROUP BY t.categoria, t.prioridad
                """, Object[].class).getResultList()) {
            String categoria = fila[0] == null ? MetricasTickets.SIN_ANALIZAR : ((Categoria) fila[0]).name();
            String prioridad = fila[1] == null ? MetricasTickets.SIN_ANALIZAR : ((Prioridad) fila[1]).name();
            long n = (Long) fila[2];
            total += n;
            porCategoria.merge(categoria, n, Long::sum);
            porPrioridad.merge(prioridad, n, Long::sum);
            matriz.add(new Celda(categoria, prioridad, n));
        }
        return new MetricasTickets(total, porCategoria, porPrioridad,
                agrupar("estado", EstadoTicket.values()), agrupar("estadoAnalisis", EstadoAnalisis.values()), matriz);
    }

    private Map<String, Long> agrupar(String campo, Enum<?>[] valores) {
        Map<String, Long> resultado = ceros(valores, false);
        for (Object[] fila : em.createQuery("SELECT t.%s, COUNT(t) FROM TicketEntity t WHERE t.eliminadoEn IS NULL GROUP BY t.%s"
                .formatted(campo, campo), Object[].class).getResultList()) {
            resultado.put(((Enum<?>) fila[0]).name(), (Long) fila[1]);
        }
        return resultado;
    }

    /** Mapa con todas las claves del enum en 0, para una forma de respuesta estable. */
    private static Map<String, Long> ceros(Enum<?>[] valores, boolean conSinAnalizar) {
        Map<String, Long> mapa = new LinkedHashMap<>();
        for (Enum<?> v : valores) {
            mapa.put(v.name(), 0L);
        }
        if (conSinAnalizar) {
            mapa.put(MetricasTickets.SIN_ANALIZAR, 0L);
        }
        return mapa;
    }
}
