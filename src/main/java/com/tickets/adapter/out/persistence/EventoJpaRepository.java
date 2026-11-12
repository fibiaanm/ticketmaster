package com.tickets.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

interface EventoJpaRepository extends JpaRepository<EventoEntity, Long>, JpaSpecificationExecutor<EventoEntity> {
}
