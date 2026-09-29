package com.task.userauth.user;

import com.task.userauth.config.JwtConfig;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static com.task.userauth.support.ApiTestSupport.AUTH_COOKIE;
import static com.task.userauth.support.ApiTestSupport.login;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class UserControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Test
    void meWithoutCookieReturns401ProblemDetail() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void meWithValidCookieReturnsProfileWithoutPassword() throws Exception {
        Cookie authCookie = login(mockMvc, "demo", "Demo12345");

        mockMvc.perform(get("/api/users/me").cookie(authCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("demo"))
                .andExpect(jsonPath("$.email").value("demo@example.com"))
                .andExpect(jsonPath("$.fullName").value("Demo User"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(content().string(not(containsString("password"))));
    }

    @Test
    void authenticatedRequestDoesNotRotateCsrfToken() throws Exception {
        Cookie authCookie = login(mockMvc, "demo", "Demo12345");

        mockMvc.perform(get("/api/users/me").cookie(authCookie, new Cookie("XSRF-TOKEN", "existing-token")))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Set-Cookie"));
    }

    @Test
    void meWithExpiredTokenReturns401() throws Exception {
        Instant past = Instant.now().minusSeconds(3600);
        String expired = token(past, past.plusSeconds(60), "user-auth");

        mockMvc.perform(get("/api/users/me").cookie(new Cookie(AUTH_COOKIE, expired)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void meWithTamperedTokenReturns401() throws Exception {
        String token = login(mockMvc, "demo", "Demo12345").getValue();
        char last = token.charAt(token.length() - 1);
        String tampered = token.substring(0, token.length() - 1) + (last == 'A' ? 'B' : 'A');

        mockMvc.perform(get("/api/users/me").cookie(new Cookie(AUTH_COOKIE, tampered)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void meWithTokenFromAnotherIssuerReturns401() throws Exception {
        Instant now = Instant.now();
        String foreign = token(now, now.plusSeconds(600), "someone-else");

        mockMvc.perform(get("/api/users/me").cookie(new Cookie(AUTH_COOKIE, foreign)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void apiResponsesCarryStrictSecurityHeaders() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(header()
                        .string("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'"))
                .andExpect(header()
                        .string("X-Content-Type-Options", "nosniff"));
    }

    private String token(Instant issuedAt, Instant expiresAt, String issuer) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject("demo")
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim(JwtConfig.ROLES_CLAIM, List.of("USER"))
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }
}
