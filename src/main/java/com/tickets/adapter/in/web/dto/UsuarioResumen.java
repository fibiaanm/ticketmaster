package com.tickets.adapter.in.web.dto;

import com.tickets.domain.Usuario;

public record UsuarioResumen(Long id, String nombre) {
    public static UsuarioResumen desde(Usuario u) {
        return u == null ? null : new UsuarioResumen(u.id(), u.nombre());
    }
}
