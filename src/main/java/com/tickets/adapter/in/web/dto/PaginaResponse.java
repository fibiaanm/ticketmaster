package com.tickets.adapter.in.web.dto;

import com.tickets.application.Pagina;

import java.util.List;

public record PaginaResponse<T>(List<T> contenido, int pagina, int tamano, long totalElementos, int totalPaginas) {

    public static <T> PaginaResponse<T> desde(Pagina<T> p) {
        int total = p.tamano() == 0 ? 0 : (int) Math.ceil((double) p.totalElementos() / p.tamano());
        return new PaginaResponse<>(p.contenido(), p.pagina(), p.tamano(), p.totalElementos(), total);
    }
}
