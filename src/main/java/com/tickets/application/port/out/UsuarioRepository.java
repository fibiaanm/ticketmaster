package com.tickets.application.port.out;

import com.tickets.domain.Usuario;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UsuarioRepository {
    Optional<Usuario> buscarPorId(Long id);

    List<Usuario> buscarPorIds(Collection<Long> ids);

    Optional<Usuario> buscarPorCorreo(String correo);

    Usuario guardar(Usuario usuario);
}
