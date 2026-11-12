package com.tickets.application;

import java.util.List;
import java.util.function.Function;

public record Pagina<T>(List<T> contenido, int pagina, int tamano, long totalElementos) {
    public <R> Pagina<R> map(Function<T, R> f) {
        return new Pagina<>(contenido.stream().map(f).toList(), pagina, tamano, totalElementos);
    }
}
