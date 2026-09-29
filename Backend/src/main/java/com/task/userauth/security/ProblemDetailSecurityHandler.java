package com.task.userauth.security;

import com.task.userauth.common.ProblemDetailWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.stereotype.Component;

import java.io.IOException;

/** Writes 401 and 403 responses from the security filter chain as RFC 9457 ProblemDetail JSON. */
@Component
public class ProblemDetailSecurityHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private static final Logger log = LoggerFactory.getLogger(ProblemDetailSecurityHandler.class);

    private final ProblemDetailWriter writer;

    public ProblemDetailSecurityHandler(ProblemDetailWriter writer) {
        this.writer = writer;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException ex)
            throws IOException {
        log.debug("401 {} {}: {}", request.getMethod(), request.getRequestURI(), ex.getMessage());
        writer.write(request, response, HttpStatus.UNAUTHORIZED, "Authentication is required to access this resource.");
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex)
            throws IOException {
        boolean csrf = ex instanceof CsrfException;
        // Worth a WARN: either a misbehaving client or someone probing endpoints they may not use.
        log.warn("403 {} {} for '{}': {}", request.getMethod(), request.getRequestURI(),
                request.getRemoteUser(), csrf ? "missing or invalid CSRF token" : "access denied");
        writer.write(request, response, HttpStatus.FORBIDDEN, csrf
                ? "Missing or invalid CSRF token."
                : "You do not have permission to access this resource.");
    }
}
