package com.tickets.application.port.out;

import com.tickets.domain.PromptTemplate;

import java.util.Optional;

public interface PromptTemplateRepository {
    Optional<PromptTemplate> buscarActivo(String nombre);

    Optional<PromptTemplate> buscar(String nombre, int version);

    PromptTemplate guardar(PromptTemplate promptTemplate);

    void activarExclusivamente(String nombre, int version);
}
