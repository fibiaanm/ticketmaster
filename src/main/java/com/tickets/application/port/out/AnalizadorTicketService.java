package com.tickets.application.port.out;

/** Puerto de analisis de tickets (IA). Reemplazable sin tocar la logica principal. */
public interface AnalizadorTicketService {
    AnalisisTicketResponse analizar(String titulo, String descripcion);
}
