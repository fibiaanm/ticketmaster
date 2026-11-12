package com.tickets.config;

import com.tickets.adapter.in.bootstrap.DevDataSeeder;
import com.tickets.adapter.out.security.BCryptPasswordHasher;
import com.tickets.adapter.out.security.JwtEmisorTokens;
import com.tickets.application.port.out.EmisorTokens;
import com.tickets.application.port.out.PasswordHasher;
import com.tickets.application.port.out.SesionRepository;
import com.tickets.application.port.out.UsuarioRepository;
import com.tickets.application.service.AutenticacionService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.time.Clock;
import java.time.Duration;

/** Autenticacion: hash de contrasenas, JWT, sesiones y seeders de desarrollo. */
@Configuration
public class AuthConfig {

    @Bean
    PasswordHasher passwordHasher() {
        return new BCryptPasswordHasher();
    }

    @Bean
    EmisorTokens emisorTokens(@Value("${security.jwt.secret}") String secreto,
                              @Value("${security.jwt.ttl-minutes:60}") long ttlMinutos, Clock clock) {
        return new JwtEmisorTokens(secreto, Duration.ofMinutes(ttlMinutos), clock);
    }

    @Bean
    AutenticacionService autenticacionService(UsuarioRepository usuarios, SesionRepository sesiones,
                                              PasswordHasher hasher, EmisorTokens emisor, Clock clock,
                                              @Value("${security.session.ttl-minutes:480}") long ttlMinutos) {
        return new AutenticacionService(usuarios, sesiones, hasher, emisor, clock, Duration.ofMinutes(ttlMinutos));
    }

    @Bean
    @Profile("dev")
    CommandLineRunner sembrarDatosDev(UsuarioRepository usuarios, PasswordHasher hasher, Clock clock) {
        DevDataSeeder seeder = new DevDataSeeder(usuarios, hasher, clock);
        return args -> seeder.sembrar();
    }
}
