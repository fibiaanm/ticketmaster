package com.tickets.adapter.out.ai;

import com.tickets.application.port.out.AnalisisTicketResponse;
import com.tickets.application.port.out.AnalizadorTicketService;
import com.tickets.shared.log.Log;

import java.util.Map;

/** Intenta el analizador principal (LLM); ante cualquier fallo usa el de respaldo (reglas locales). */
public class AnalizadorConFallback implements AnalizadorTicketService {

    private final AnalizadorTicketService principal;
    private final AnalizadorTicketService respaldo;

    public AnalizadorConFallback(AnalizadorTicketService principal, AnalizadorTicketService respaldo) {
        this.principal = principal;
        this.respaldo = respaldo;
    }

    @Override
    public AnalisisTicketResponse analizar(String titulo, String descripcion) {
        try {
            return principal.analizar(titulo, descripcion);
        } catch (RuntimeException e) {
            Log.warn("Analisis con IA fallido; se usa el clasificador local",
                    Map.of("causa", e.getClass().getSimpleName(), "detalle", String.valueOf(e.getMessage())));
            return respaldo.analizar(titulo, descripcion);
        }
    }
}
