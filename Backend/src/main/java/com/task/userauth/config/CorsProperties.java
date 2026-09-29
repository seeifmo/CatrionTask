package com.task.userauth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Origins allowed to call the API cross-origin with credentials. Empty by default: in development the
 * Angular dev server proxies {@code /api}, so browser calls are same-origin and CORS is not needed.
 */
@ConfigurationProperties("app.cors")
public record CorsProperties(List<String> allowedOrigins) {

    public CorsProperties {
        allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
    }
}
