package com.tickets.adapter.out.ai;

import com.tickets.application.port.out.AnalisisTicketResponse;
import com.tickets.application.port.out.AnalizadorTicketService;
import com.tickets.application.port.out.PromptTemplateRepository;
import com.tickets.domain.OrigenAnalisis;
import com.tickets.domain.PromptTemplate;
import com.tickets.domain.exception.ConexionIAException;

/** Opcion A: analisis con un modelo de lenguaje (via la fachada TicketAIAnalysis). */
public class LlmAnalizador implements AnalizadorTicketService {

    public static final String PROMPT_NOMBRE = "ticket-analysis";

    private final TicketAIAnalysis fachada;
    private final PromptTemplateRepository prompts;
    private final RespuestaIAParser parser;

    public LlmAnalizador(TicketAIAnalysis fachada, PromptTemplateRepository prompts, RespuestaIAParser parser) {
        this.fachada = fachada;
        this.prompts = prompts;
        this.parser = parser;
    }

    @Override
    public AnalisisTicketResponse analizar(String titulo, String descripcion) {
        PromptTemplate prompt = prompts.buscarActivo(PROMPT_NOMBRE).orElseThrow(() ->
                new ConexionIAException("No hay prompt activo '%s'".formatted(PROMPT_NOMBRE), false, null));

        var respuesta = fachada.analyze(prompt.systemPrompt(), prompt.renderUsuario(titulo, descripcion));
        return parser.parsear(respuesta.contenido())
                .conTrazabilidad(OrigenAnalisis.LLM, respuesta.modelo(), prompt.id());
    }
}
