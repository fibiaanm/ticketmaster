package com.tickets.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;

interface TareaJpaRepository extends JpaRepository<TareaEntity, Long> {

    /**
     * Reclama una tarea: pendiente y disponible, o "procesando" abandonada (worker caido).
     * SKIP LOCKED permite varios workers sin pisarse.
     */
    @Query(value = """
            SELECT * FROM tarea
             WHERE (estado = 'PENDIENTE' AND disponible_desde <= :ahora)
                OR (estado = 'PROCESANDO' AND fecha_actualizacion <= :vencido)
             ORDER BY id
             LIMIT 1
             FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    Optional<TareaEntity> reclamarSiguiente(Instant ahora, Instant vencido);
}
