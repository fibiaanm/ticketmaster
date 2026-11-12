package com.tickets.adapter.out.persistence;

import com.tickets.application.port.out.UsuarioRepository;
import com.tickets.domain.Usuario;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Component("usuarioRepositoryJpa")
@Transactional
class JpaUsuarioRepository implements UsuarioRepository {

    private final UsuarioJpaRepository jpa;

    JpaUsuarioRepository(UsuarioJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Usuario> buscarPorId(Long id) {
        return jpa.findById(id).map(JpaUsuarioRepository::aDominio);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Usuario> buscarPorIds(Collection<Long> ids) {
        return ids.isEmpty() ? List.of()
                : jpa.findAllById(ids).stream().map(JpaUsuarioRepository::aDominio).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Usuario> buscarPorCorreo(String correo) {
        return jpa.findByCorreoIgnoreCase(correo).map(JpaUsuarioRepository::aDominio);
    }

    @Override
    public Usuario guardar(Usuario u) {
        UsuarioEntity e = new UsuarioEntity();
        e.id = u.id();
        e.nombre = u.nombre();
        e.correo = u.correo();
        e.password = u.passwordHash();
        e.rol = u.rol();
        e.fechaCreacion = u.fechaCreacion();
        return aDominio(jpa.save(e));
    }

    static Usuario aDominio(UsuarioEntity e) {
        return new Usuario(e.id, e.nombre, e.correo, e.password, e.rol, e.fechaCreacion);
    }
}
