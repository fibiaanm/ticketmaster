package com.tickets.support;

import com.tickets.application.port.out.PromptTemplateRepository;
import com.tickets.domain.PromptTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class InMemoryPromptRepository implements PromptTemplateRepository {

    public final List<PromptTemplate> items = new ArrayList<>();
    private long seq = 0;

    @Override
    public Optional<PromptTemplate> buscarActivo(String nombre) {
        return items.stream().filter(p -> p.nombre().equals(nombre) && p.activo()).findFirst();
    }

    @Override
    public Optional<PromptTemplate> buscar(String nombre, int version) {
        return items.stream().filter(p -> p.nombre().equals(nombre) && p.version() == version).findFirst();
    }

    @Override
    public PromptTemplate guardar(PromptTemplate p) {
        PromptTemplate guardado = new PromptTemplate(++seq, p.nombre(), p.version(), p.systemPrompt(),
                p.userTemplate(), p.activo(), p.hash());
        items.add(guardado);
        return guardado;
    }

    @Override
    public void activarExclusivamente(String nombre, int version) {
        List<PromptTemplate> nuevos = items.stream().map(p -> p.nombre().equals(nombre)
                ? new PromptTemplate(p.id(), p.nombre(), p.version(), p.systemPrompt(), p.userTemplate(),
                p.version() == version, p.hash()) : p).toList();
        items.clear();
        items.addAll(nuevos);
    }
}
