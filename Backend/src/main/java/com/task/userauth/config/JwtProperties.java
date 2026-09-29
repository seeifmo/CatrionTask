package com.task.userauth.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * @param secret HMAC key for HS256; at least 32 characters (256 bits). Supplied via {@code JWT_SECRET}.
 * @param ttl    how long an access token (and its cookie) stays valid
 * @param issuer value of the {@code iss} claim, checked on every request
 */
@Validated
@ConfigurationProperties("app.jwt")
public record JwtProperties(
        @NotBlank @Size(min = 32, message = "must be at least 32 characters (256 bits)") String secret,
        @NotNull Duration ttl,
        @NotBlank String issuer) {
}
