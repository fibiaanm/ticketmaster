package com.tickets.adapter.in.bootstrap;

import com.tickets.application.port.out.PasswordHasher;
import com.tickets.domain.Rol;
import com.tickets.support.InMemoryUsuarioRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;

class DevDataSeederTest {

    @Test
    void creaUnUsuarioPorRolConPasswordPwYEsIdempotente() {
        var repo = new InMemoryUsuarioRepository();
        PasswordHasher hasher = new PasswordHasher() {
            public String hash(String p) { return "h:" + p; }
            public boolean coincide(String p, String h) { return h.equals("h:" + p); }
        };
        var seeder = new DevDataSeeder(repo, hasher, Clock.systemUTC());

        seeder.sembrar();
        seeder.sembrar();

        // InMemoryUsuarioRepository indexa por id: el seeder no asigna ids, asi que cuenta por correo
        for (Rol rol : Rol.values()) {
            var u = repo.buscarPorCorreo(rol.name().toLowerCase() + "@tickets.local");
            assertThat(u).isPresent();
            assertThat(u.get().nombre()).isEqualTo(rol.name());
            assertThat(u.get().rol()).isEqualTo(rol);
            assertThat(hasher.coincide("pw", u.get().passwordHash())).isTrue();
        }
    }
}
