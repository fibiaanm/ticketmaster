package com.tickets.adapter.out.cache;

import com.tickets.application.port.out.UsuarioRepository;
import com.tickets.domain.Usuario;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Cachea la busqueda por id (se consulta en cada peticion autenticada con sesion y al validar solicitante o
 * tecnico). SEGURIDAD: en cache se guarda el usuario SIN el hash de la contrasena. El login busca por correo
 * y nunca pasa por la cache.
 */
public class UsuarioRepositorioConCache implements UsuarioRepository {

    private final UsuarioRepository delegado;
    private final CacheAside cache;
    private final Duration ttl;

    public UsuarioRepositorioConCache(UsuarioRepository delegado, CacheAside cache, Duration ttl) {
        this.delegado = delegado;
        this.cache = cache;
        this.ttl = ttl;
    }

    @Override
    public Optional<Usuario> buscarPorId(Long id) {
        return cache.obtener("usuario:" + id, ttl, Usuario.class,
                () -> delegado.buscarPorId(id).map(UsuarioRepositorioConCache::sinHash));
    }

    @Override
    public List<Usuario> buscarPorIds(Collection<Long> ids) {
        return delegado.buscarPorIds(ids);
    }

    @Override
    public Optional<Usuario> buscarPorCorreo(String correo) {
        return delegado.buscarPorCorreo(correo);
    }

    @Override
    public Usuario guardar(Usuario usuario) {
        Usuario guardado = delegado.guardar(usuario);
        cache.eliminar("usuario:" + guardado.id());
        return guardado;
    }

    private static Usuario sinHash(Usuario u) {
        return new Usuario(u.id(), u.nombre(), u.correo(), "", u.rol(), u.fechaCreacion());
    }
}
