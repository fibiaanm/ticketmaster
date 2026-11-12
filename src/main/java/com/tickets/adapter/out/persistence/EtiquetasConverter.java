package com.tickets.adapter.out.persistence;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Arrays;
import java.util.List;

/** Lista de etiquetas <-> "a,b,c" (las etiquetas no contienen comas: se sanean al guardar). */
@Converter
class EtiquetasConverter implements AttributeConverter<List<String>, String> {

    @Override
    public String convertToDatabaseColumn(List<String> etiquetas) {
        if (etiquetas == null || etiquetas.isEmpty()) {
            return null;
        }
        return String.join(",", etiquetas.stream().map(e -> e.replace(",", " ")).toList());
    }

    @Override
    public List<String> convertToEntityAttribute(String valor) {
        return valor == null || valor.isBlank() ? List.of() : Arrays.asList(valor.split(","));
    }
}
