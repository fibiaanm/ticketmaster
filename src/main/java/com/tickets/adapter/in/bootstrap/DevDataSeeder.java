package com.tickets.adapter.in.bootstrap;

import com.tickets.application.port.out.PasswordHasher;
import com.tickets.application.port.out.UsuarioRepository;
import com.tickets.domain.Rol;
import com.tickets.domain.Usuario;
import com.tickets.shared.log.Log;

import java.time.Clock;
import java.util.Map;

/** Seeder de desarrollo: un usuario por rol; nombre = rol, password = "pw". Solo perfil dev. */
public class DevDataSeeder {

    private final UsuarioRepository usuarios;
    private final PasswordHasher hasher;
    private final Clock reloj;

    public DevDataSeeder(UsuarioRepository usuarios, PasswordHasher hasher, Clock reloj) {
        this.usuarios = usuarios;
        this.hasher = hasher;
        this.reloj = reloj;
    }

    public void sembrar() {
        for (Rol rol : Rol.values()) {
            String correo = rol.name().toLowerCase() + "@tickets.local";
            if (usuarios.buscarPorCorreo(correo).isEmpty()) {
                usuarios.guardar(new Usuario(null, rol.name(), correo, hasher.hash("pw"), rol, reloj.instant()));
                Log.info("Usuario de desarrollo creado", Map.of("correo", correo, "rol", rol.name()));
            }
        }
    }
}
