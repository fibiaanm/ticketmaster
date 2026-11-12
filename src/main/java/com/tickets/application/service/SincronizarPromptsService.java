package com.tickets.application.service;

import com.tickets.application.port.out.PromptDefinicion;
import com.tickets.application.port.out.PromptSource;
import com.tickets.application.port.out.PromptTemplateRepository;
import com.tickets.domain.PromptTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

/**
 * Carga a la BD las versiones de prompts definidas en archivos. Las versiones son inmutables:
 * si el contenido de una version existente cambia, se aborta (hay que crear una version nueva).
 */
public class SincronizarPromptsService {

    private final PromptSource source;
    private final PromptTemplateRepository repository;

    public SincronizarPromptsService(PromptSource source, PromptTemplateRepository repository) {
        this.source = source;
        this.repository = repository;
    }

    public void sincronizar() {
        List<PromptDefinicion> definiciones = source.cargar().stream()
                .sorted(Comparator.comparing(PromptDefinicion::nombre).thenComparingInt(PromptDefinicion::version))
                .toList();

        for (PromptDefinicion d : definiciones) {
            String hash = hash(d);
            Optional<PromptTemplate> existente = repository.buscar(d.nombre(), d.version());
            if (existente.isPresent()) {
                if (!existente.get().hash().equals(hash)) {
                    throw new IllegalStateException(("El prompt %s v%d es inmutable pero su contenido cambió; "
                            + "cree una nueva versión").formatted(d.nombre(), d.version()));
                }
                continue;
            }
            repository.guardar(new PromptTemplate(null, d.nombre(), d.version(), d.systemPrompt(),
                    d.userTemplate(), false, hash));
            // la version mas alta recien cargada pasa a ser la activa
            boolean esLaMasAlta = definiciones.stream().filter(o -> o.nombre().equals(d.nombre()))
                    .allMatch(o -> o.version() <= d.version());
            if (esLaMasAlta) {
                repository.activarExclusivamente(d.nombre(), d.version());
            }
        }
        // garantiza una version activa por nombre
        definiciones.stream().map(PromptDefinicion::nombre).distinct().forEach(nombre -> {
            if (repository.buscarActivo(nombre).isEmpty()) {
                int max = definiciones.stream().filter(o -> o.nombre().equals(nombre))
                        .mapToInt(PromptDefinicion::version).max().orElseThrow();
                repository.activarExclusivamente(nombre, max);
            }
        });
    }

    static String hash(PromptDefinicion d) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            byte[] digest = sha.digest((d.systemPrompt() + "\n---\n" + d.userTemplate()).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
