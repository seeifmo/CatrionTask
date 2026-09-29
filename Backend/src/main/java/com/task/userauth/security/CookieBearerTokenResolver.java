package com.task.userauth.security;

import com.task.userauth.config.AuthCookieProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.util.WebUtils;

/**
 * Reads the access token from the HttpOnly auth cookie instead of the Authorization header.
 *
 * <p>The auth endpoints are skipped on purpose. They are public, and a stale or expired cookie must not
 * turn a fresh login (or a logout) into a 401.
 */
@Component
public class CookieBearerTokenResolver implements BearerTokenResolver {

    private static final String AUTH_ENDPOINTS_PREFIX = "/api/auth/";

    private final String cookieName;

    public CookieBearerTokenResolver(AuthCookieProperties properties) {
        this.cookieName = properties.name();
    }

    @Override
    public String resolve(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (path.startsWith(AUTH_ENDPOINTS_PREFIX)) {
            return null;
        }
        Cookie cookie = WebUtils.getCookie(request, cookieName);
        return cookie != null && StringUtils.hasText(cookie.getValue()) ? cookie.getValue() : null;
    }
}
