package com.task.userauth.security;

import com.task.userauth.config.JwtConfig;
import com.task.userauth.config.JwtProperties;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidationException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TokenServiceTest {

    private static final JwtProperties PROPERTIES =
            new JwtProperties("unit-test-secret-0123456789abcdefghij", Duration.ofMinutes(30), "user-auth");

    private final JwtConfig jwtConfig = new JwtConfig(PROPERTIES);
    private final JwtDecoder decoder = jwtConfig.jwtDecoder();

    @Test
    void issuesTokenWithExpectedClaims() {
        Instant now = Instant.now();
        TokenService service = serviceAt(now);

        Jwt jwt = decoder.decode(service.issue(demoAuthentication()));

        assertThat(jwt.getSubject()).isEqualTo("demo");
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("user-auth");
        assertThat(jwt.getClaimAsStringList(JwtConfig.ROLES_CLAIM)).containsExactly("USER");
        assertThat(jwt.getIssuedAt()).isEqualTo(now.truncatedTo(ChronoUnit.SECONDS));
        assertThat(jwt.getExpiresAt()).isEqualTo(jwt.getIssuedAt().plus(Duration.ofMinutes(30)));
        assertThat(jwt.getId()).isNotBlank();
    }

    @Test
    void tokenIsRejectedOnceExpired() {
        TokenService service = serviceAt(Instant.now().minus(Duration.ofHours(1)));

        String token = service.issue(demoAuthentication());

        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtValidationException.class);
    }

    private TokenService serviceAt(Instant instant) {
        return new TokenService(jwtConfig.jwtEncoder(), PROPERTIES, Clock.fixed(instant, ZoneOffset.UTC));
    }

    private static UsernamePasswordAuthenticationToken demoAuthentication() {
        return UsernamePasswordAuthenticationToken.authenticated("demo", null,
                AuthorityUtils.createAuthorityList("ROLE_USER"));
    }
}
