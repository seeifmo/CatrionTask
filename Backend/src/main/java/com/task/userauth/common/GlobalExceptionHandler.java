package com.task.userauth.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Central error handling for controllers. Every error becomes an RFC 9457 ProblemDetail with
 * {@code timestamp} and {@code requestId} (see {@link ProblemDetails}). Spring MVC's own exceptions
 * (bad JSON, wrong method, parameter validation, and so on) come from the base class and are enriched
 * the same way.
 *
 * <p>Logging policy: client mistakes (4xx) are logged briefly at DEBUG/INFO, security-relevant events
 * at WARN, and only real server faults (5xx) at ERROR with a stack trace. The client never sees internals.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Adds an {@code errors} map (field → message) so the client can highlight each invalid field. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(@NonNull MethodArgumentNotValidException ex,
                                                                  @NonNull HttpHeaders headers,
                                                                  @NonNull HttpStatusCode status,
                                                                  @NonNull WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        log.debug("400 validation failed on fields {}", errors.keySet());
        ProblemDetail problem = ProblemDetails.of(HttpStatus.BAD_REQUEST, "Some fields are invalid.");
        problem.setProperty("errors", errors);
        return ResponseEntity.badRequest().body(problem);
    }

    /** Enriches the ProblemDetails built by the base class for Spring MVC exceptions. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(@NonNull Exception ex, @Nullable Object body,
                                                             @NonNull HttpHeaders headers,
                                                             @NonNull HttpStatusCode statusCode,
                                                             @NonNull WebRequest request) {
        ResponseEntity<Object> response = super.handleExceptionInternal(ex, body, headers, statusCode, request);
        if (response != null && response.getBody() instanceof ProblemDetail problem) {
            ProblemDetails.enrich(problem);
        }
        log.debug("{} {}: {}", statusCode.value(), ex.getClass().getSimpleName(), ex.getMessage());
        return response;
    }

    /** Same message for unknown user and wrong password, so usernames can't be probed. */
    @ExceptionHandler(AuthenticationException.class)
    ProblemDetail handleAuthentication(AuthenticationException ex) {
        // INFO, not WARN: one failed login is normal; a burst of them is what the rate limiter is for.
        log.info("401 authentication failed: {}", ex.getClass().getSimpleName());
        return ProblemDetails.of(HttpStatus.UNAUTHORIZED, "Invalid username or password.");
    }

    @ExceptionHandler(DuplicateUserException.class)
    ProblemDetail handleDuplicate(DuplicateUserException ex) {
        log.debug("409 duplicate {}", ex.getField());
        ProblemDetail problem = ProblemDetails.of(HttpStatus.CONFLICT, ex.getMessage());
        if (ex.getField() != null) {
            problem.setProperty("errors", Map.of(ex.getField(), ex.getMessage()));
        }
        return problem;
    }

    /**
     * A {@code @PreAuthorize} check failed inside a controller. Without this, the catch-all below would
     * turn it into a 500 before Spring Security's filter could answer with 403.
     */
    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail handleAccessDenied(AccessDeniedException ex) {
        log.warn("403 method-level access denied: {}", ex.getMessage());
        return ProblemDetails.of(HttpStatus.FORBIDDEN, "You do not have permission to access this resource.");
    }

    /** Last resort: log the cause with its stack trace, but never leak internals to the client. */
    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception ex) {
        log.error("500 unhandled exception", ex);
        return ProblemDetails.of(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred.");
    }
}
