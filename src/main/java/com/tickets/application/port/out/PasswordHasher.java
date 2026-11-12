package com.tickets.application.port.out;

public interface PasswordHasher {
    String hash(String passwordPlano);

    boolean coincide(String passwordPlano, String hash);
}
