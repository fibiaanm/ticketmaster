package com.tickets.domain;

import com.tickets.domain.exception.SolicitudInvalidaException;

import java.util.Arrays;

public final class EnumParser {

    private EnumParser() {
    }

    public static <E extends Enum<E>> E parsear(Class<E> tipo, String valor, String mensajeBase) {
        if (valor != null) {
            String normalizado = valor.trim().toUpperCase();
            for (E constante : tipo.getEnumConstants()) {
                if (constante.name().equals(normalizado)) {
                    return constante;
                }
            }
        }
        throw new SolicitudInvalidaException("%s: '%s'. Valores permitidos: %s"
                .formatted(mensajeBase, valor == null ? "" : valor.trim(), Arrays.toString(tipo.getEnumConstants())));
    }
}
