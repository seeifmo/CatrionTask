package com.task.userauth.ratelimit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.task.userauth.common.ProblemDetailWriter;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Token-bucket rate limiting per client IP, using Bucket4j.
 *
 * <ul>
 *   <li>POST {@code /api/auth/login} and {@code /api/auth/register} share a strict budget (password
 *       guessing, account spam).</li>
 *   <li>Every other {@code /api/**} request has a looser budget.</li>
 *   <li>Over budget: {@code 429 Too Many Requests}, a {@code Retry-After} header and a ProblemDetail body.
 *       Allowed responses carry {@code X-RateLimit-Remaining}.</li>
 * </ul>
 *
 * <p>It runs before Spring Security, so rejected requests cost no BCrypt or JWT work. Buckets live in a
 * size-bounded Caffeine cache that expires idle clients, so a flood of distinct IPs can't exhaust memory.
 *
 * <p>Limits:
 * <ul>
 *   <li>The key is {@code request.getRemoteAddr()}. Behind a proxy, set
 *       {@code server.forward-headers-strategy=native} with the proxy as a trusted address.
 *       X-Forwarded-For is deliberately not read directly, because clients can spoof it.</li>
 *   <li>Buckets are per instance. With several replicas, use Bucket4j's Redis or JCache backend
 *       for a shared limit.</li>
 * </ul>
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter implements Ordered {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);
    private static final Set<String> AUTH_PATHS = Set.of("/api/auth/login", "/api/auth/register");

    private final RateLimitProperties properties;
    private final ProblemDetailWriter problemWriter;
    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
            .maximumSize(100_000)
            .expireAfterAccess(Duration.ofMinutes(10))
            .build();

    public RateLimitFilter(RateLimitProperties properties, ProblemDetailWriter problemWriter) {
        this.properties = properties;
        this.problemWriter = problemWriter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !properties.enabled() || !path(request).startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        boolean auth = HttpMethod.POST.matches(request.getMethod()) && AUTH_PATHS.contains(path(request));
        RateLimitProperties.Tier tier = auth ? properties.auth() : properties.api();
        String key = (auth ? "auth:" : "api:") + request.getRemoteAddr();

        ConsumptionProbe probe = buckets.get(key, k -> newBucket(tier)).tryConsumeAndReturnRemaining(1);
        if (probe.isConsumed()) {
            response.setHeader("X-RateLimit-Remaining", Long.toString(probe.getRemainingTokens()));
            chain.doFilter(request, response);
            return;
        }

        long retryAfter = Math.max(1, TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill()) + 1);
        log.warn("429 rate limit exceeded: {} {} from {} (retry in {}s)",
                request.getMethod(), path(request), request.getRemoteAddr(), retryAfter);
        response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(retryAfter));
        response.setHeader("X-RateLimit-Remaining", "0");
        problemWriter.write(request, response, HttpStatus.TOO_MANY_REQUESTS,
                "Too many requests. Please try again in " + retryAfter + " seconds.");
    }

    /** After RequestIdFilter (so 429s are logged with the request ID), before Spring Security (-100). */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }

    private static Bucket newBucket(RateLimitProperties.Tier tier) {
        return Bucket.builder()
                .addLimit(limit -> limit.capacity(tier.capacity()).refillGreedy(tier.capacity(), tier.period()))
                .build();
    }

    private static String path(HttpServletRequest request) {
        return request.getRequestURI().substring(request.getContextPath().length());
    }
}
