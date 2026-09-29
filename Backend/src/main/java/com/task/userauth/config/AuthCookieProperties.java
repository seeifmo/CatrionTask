package com.task.userauth.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param name   cookie that carries the access token
 * @param secure send the cookie over HTTPS only; must be true outside local development
 */
@Validated
@ConfigurationProperties("app.auth-cookie")
public record AuthCookieProperties(@NotBlank String name, boolean secure) {
}
