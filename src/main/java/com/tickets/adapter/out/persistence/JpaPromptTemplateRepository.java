package com.tickets.adapter.out.persistence;

import com.tickets.application.port.out.PromptTemplateRepository;
import com.tickets.domain.PromptTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Optional;

@Component("promptRepositoryJpa")
@Transactional
class JpaPromptTemplateRepository implements PromptTemplateRepository {

    private final PromptTemplateJpaRepository jpa;
    private final Clock reloj;

    JpaPromptTemplateRepository(PromptTemplateJpaRepository jpa, Clock reloj) {
        this.jpa = jpa;
        this.reloj = reloj;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PromptTemplate> buscarActivo(String nombre) {
        return jpa.findFirstByNombreAndActivoTrue(nombre).map(JpaPromptTemplateRepository::aDominio);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PromptTemplate> buscar(String nombre, int version) {
        return jpa.findByNombreAndVersion(nombre, version).map(JpaPromptTemplateRepository::aDominio);
    }

    @Override
    public PromptTemplate guardar(PromptTemplate p) {
        PromptTemplateEntity e = new PromptTemplateEntity();
        e.id = p.id();
        e.nombre = p.nombre();
        e.version = p.version();
        e.systemPrompt = p.systemPrompt();
        e.userTemplate = p.userTemplate();
        e.activo = p.activo();
        e.hash = p.hash();
        e.fechaCreacion = reloj.instant();
        return aDominio(jpa.save(e));
    }

    @Override
    public void activarExclusivamente(String nombre, int version) {
        jpa.activarExclusivamente(nombre, version);
    }

    private static PromptTemplate aDominio(PromptTemplateEntity e) {
        return new PromptTemplate(e.id, e.nombre, e.version, e.systemPrompt, e.userTemplate, e.activo, e.hash);
    }
}
