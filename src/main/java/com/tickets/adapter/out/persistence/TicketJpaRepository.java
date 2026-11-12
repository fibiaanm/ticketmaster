package com.tickets.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

interface TicketJpaRepository extends JpaRepository<TicketEntity, Long>, JpaSpecificationExecutor<TicketEntity> {
}
