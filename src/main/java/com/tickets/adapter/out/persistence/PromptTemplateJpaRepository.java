package com.tickets.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

interface PromptTemplateJpaRepository extends JpaRepository<PromptTemplateEntity, Long> {
    Optional<PromptTemplateEntity> findFirstByNombreAndActivoTrue(String nombre);

    Optional<PromptTemplateEntity> findByNombreAndVersion(String nombre, int version);

    @Modifying
    @Query("UPDATE PromptTemplateEntity p SET p.activo = (p.version = :version) WHERE p.nombre = :nombre")
    void activarExclusivamente(String nombre, int version);
}
