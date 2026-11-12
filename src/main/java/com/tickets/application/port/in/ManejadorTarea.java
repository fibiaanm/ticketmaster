package com.tickets.application.port.in;

@FunctionalInterface
public interface ManejadorTarea {
    void manejar(Long ticketId);
}
