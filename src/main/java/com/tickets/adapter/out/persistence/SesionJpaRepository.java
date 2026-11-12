package com.tickets.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

interface SesionJpaRepository extends JpaRepository<SesionEntity, Long> {
    Optional<SesionEntity> findByTokenHash(String tokenHash);

    @Modifying
    @Query("UPDATE SesionEntity s SET s.revocada = true WHERE s.tokenHash = :tokenHash")
    void revocar(String tokenHash);
}
