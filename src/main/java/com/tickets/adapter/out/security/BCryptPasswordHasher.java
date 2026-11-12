package com.tickets.adapter.out.security;

import com.tickets.application.port.out.PasswordHasher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class BCryptPasswordHasher implements PasswordHasher {

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Override
    public String hash(String passwordPlano) {
        return encoder.encode(passwordPlano);
    }

    @Override
    public boolean coincide(String passwordPlano, String hash) {
        try {
            return encoder.matches(passwordPlano, hash);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
