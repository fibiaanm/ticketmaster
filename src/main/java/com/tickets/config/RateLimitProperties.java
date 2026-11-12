package com.tickets.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("ratelimit")
public record RateLimitProperties(Boolean enabled, Duration window, Integer login, Integer crearTicket,
                                  Integer reanalizar, Integer general) {
    public RateLimitProperties {
        enabled = enabled == null || enabled;
        window = window == null ? Duration.ofMinutes(1) : window;
        login = login == null ? 5 : login;
        crearTicket = crearTicket == null ? 10 : crearTicket;
        reanalizar = reanalizar == null ? 5 : reanalizar;
        general = general == null ? 120 : general;
    }
}
