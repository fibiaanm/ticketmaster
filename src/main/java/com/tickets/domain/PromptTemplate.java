package com.tickets.domain;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Version inmutable de un prompt (system prompt + plantilla de instrucciones del usuario). */
public record PromptTemplate(Long id, String nombre, int version, String systemPrompt, String userTemplate,
                             boolean activo, String hash) {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{(titulo|descripcion)}");

    /** Reemplazo en una sola pasada: el contenido insertado no se vuelve a interpretar. */
    public String renderUsuario(String titulo, String descripcion) {
        Matcher m = PLACEHOLDER.matcher(userTemplate);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String valor = m.group(1).equals("titulo") ? titulo : descripcion;
            m.appendReplacement(sb, Matcher.quoteReplacement(valor));
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
