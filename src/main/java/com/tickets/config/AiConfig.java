package com.tickets.config;

import com.tickets.adapter.out.ai.*;
import com.tickets.application.port.out.AnalizadorTicketService;
import com.tickets.application.port.out.PromptTemplateRepository;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.List;

/** IA: fachada + proveedores + clasificador local de respaldo. */
@Configuration
@EnableConfigurationProperties(AIProperties.class)
public class AiConfig {

    @Bean
    OpenAIProvider openAIProvider(AIProperties props) {
        return new OpenAIProvider(props);
    }

    @Bean
    TicketAIAnalysis ticketAIAnalysis(List<AIModelProvider> proveedores, AIProperties props) {
        return new TicketAIAnalysis(proveedores, props);
    }

    @Bean
    LlmAnalizador llmAnalizador(TicketAIAnalysis fachada, PromptTemplateRepository prompts) {
        return new LlmAnalizador(fachada, prompts, new RespuestaIAParser());
    }

    @Bean
    ReglasAnalizador reglasAnalizador() {
        return new ReglasAnalizador();
    }

    @Bean
    @Primary
    AnalizadorTicketService analizadorTicketService(LlmAnalizador llm, ReglasAnalizador reglas) {
        return new AnalizadorConFallback(llm, reglas);
    }
}
