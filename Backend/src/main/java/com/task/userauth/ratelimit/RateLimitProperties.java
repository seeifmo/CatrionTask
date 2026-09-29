package com.task.userauth.ratelimit;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Per-client-IP request budgets ({@code app.rate-limit.*}).
 *
 * @param enabled master switch
 * @param auth    budget for login and register: strict, because these are brute-force targets
 * @param api     budget for every other /api request
 */
@Validated
@ConfigurationProperties("app.rate-limit")
public record RateLimitProperties(boolean enabled, @Valid @NotNull Tier auth, @Valid @NotNull Tier api) {

    /** {@code capacity} requests per {@code period}, refilled gradually (greedy token bucket). */
    public record Tier(@Positive long capacity, @NotNull Duration period) {
    }
}
