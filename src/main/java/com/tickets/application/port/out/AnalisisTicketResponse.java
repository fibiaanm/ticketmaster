package com.tickets.application.port.out;

import java.util.List;

/**
 * Resultado del analisis. Los 4 primeros componentes son los del enunciado; el resto es trazabilidad.
 */
public record AnalisisTicketResponse(String categoria, String prioridad, String resumen, List<String> etiquetas,
                                     com.tickets.domain.OrigenAnalisis origen, String modelo,
                                     Long promptTemplateId) {

    public AnalisisTicketResponse(String categoria, String prioridad, String resumen, List<String> etiquetas) {
        this(categoria, prioridad, resumen, etiquetas, null, null, null);
    }

    public AnalisisTicketResponse conTrazabilidad(com.tickets.domain.OrigenAnalisis origen, String modelo,
                                                  Long promptTemplateId) {
        return new AnalisisTicketResponse(categoria, prioridad, resumen, etiquetas, origen, modelo, promptTemplateId);
    }
}
