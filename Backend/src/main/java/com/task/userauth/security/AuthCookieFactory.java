package com.task.userauth.security;

import com.task.userauth.config.AuthCookieProperties;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** Builds the HttpOnly cookie that carries the access token, and the cookie that clears it. */
@Component
public class AuthCookieFactory {

    /** The browser only sends the token to the API, never to static assets. */
    private static final String PATH = "/api";

    private final AuthCookieProperties properties;

    public AuthCookieFactory(AuthCookieProperties properties) {
        this.properties = properties;
    }

    public ResponseCookie create(String token, Duration maxAge) {
        return base(token).maxAge(maxAge).build();
    }

    public ResponseCookie clear() {
        return base("").maxAge(0).build();
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(properties.name(), value)
                .httpOnly(true)
                .secure(properties.secure())
                .sameSite("Strict")
                .path(PATH);
    }
}
