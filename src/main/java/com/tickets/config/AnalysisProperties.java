package com.tickets.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** analysis.mode = sync | async. */
@ConfigurationProperties("analysis")
public record AnalysisProperties(String mode, Integer maxAttempts, Integer retryDelaySeconds) {
    public AnalysisProperties {
        mode = mode == null || mode.isBlank() ? "async" : mode.toLowerCase();
        maxAttempts = maxAttempts == null ? 3 : maxAttempts;
        retryDelaySeconds = retryDelaySeconds == null ? 30 : retryDelaySeconds;
    }
}
