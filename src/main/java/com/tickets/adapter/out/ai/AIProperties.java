package com.tickets.adapter.out.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configuracion desacoplada del proveedor: AI_PROVIDER, AI_MODEL, OPENAI_API_KEY... */
@ConfigurationProperties("ai")
public record AIProperties(String provider, String model, String apiKey, String baseUrl,
                           Integer timeoutSeconds, Integer maxRetries, Long retryBackoffMs) {

    public AIProperties {
        provider = provider == null || provider.isBlank() ? "openai" : provider;
        model = model == null || model.isBlank() ? "gpt-5.4-mini" : model;
        baseUrl = baseUrl == null || baseUrl.isBlank() ? "https://api.openai.com/v1" : baseUrl;
        timeoutSeconds = timeoutSeconds == null ? 20 : timeoutSeconds;
        maxRetries = maxRetries == null ? 1 : maxRetries;
        retryBackoffMs = retryBackoffMs == null ? 300L : retryBackoffMs;
    }
}
