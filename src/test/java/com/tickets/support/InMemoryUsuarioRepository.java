package com.tickets.support;

import com.tickets.application.port.out.UsuarioRepository;
import com.tickets.domain.Rol;
import com.tickets.domain.Usuario;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class InMemoryUsuarioRepository implements UsuarioRepository {

    public final Map<Long, Usuario> items = new HashMap<>();

    public InMemoryUsuarioRepository con(long id, Rol rol) {
        items.put(id, new Usuario(id, rol.name(), rol.name().toLowerCase() + id + "@t.com", "hash", rol, Instant.EPOCH));
        return this;
    }

    @Override
    public Optional<Usuario> buscarPorId(Long id) {
        return Optional.ofNullable(items.get(id));
    }

    @Override
    public java.util.List<Usuario> buscarPorIds(java.util.Collection<Long> ids) {
        return ids.stream().map(items::get).filter(java.util.Objects::nonNull).toList();
    }

    @Override
    public Optional<Usuario> buscarPorCorreo(String correo) {
        return items.values().stream().filter(u -> u.correo().equalsIgnoreCase(correo)).findFirst();
    }

    @Override
    public Usuario guardar(Usuario u) {
        Usuario guardado = u.id() != null ? u
                : new Usuario(items.keySet().stream().mapToLong(Long::longValue).max().orElse(0) + 1,
                u.nombre(), u.correo(), u.passwordHash(), u.rol(), u.fechaCreacion());
        items.put(guardado.id(), guardado);
        return guardado;
    }
}
