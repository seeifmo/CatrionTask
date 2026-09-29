package com.task.userauth.common;

import com.task.userauth.logging.RequestIdFilter;
import org.slf4j.MDC;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

import java.time.Instant;

/**
 * Builds every error body the API returns, so they all share one shape: RFC 9457 fields plus
 * {@code timestamp} and {@code requestId} (the same ID as in the logs and the X-Request-Id header).
 */
public final class ProblemDetails {

    private ProblemDetails() {
    }

    public static ProblemDetail of(HttpStatusCode status, String detail) {
        return enrich(ProblemDetail.forStatusAndDetail(status, detail));
    }

    public static ProblemDetail enrich(ProblemDetail problem) {
        problem.setProperty("timestamp", Instant.now());
        String requestId = MDC.get(RequestIdFilter.MDC_KEY);
        if (requestId != null) {
            problem.setProperty("requestId", requestId);
        }
        return problem;
    }
}
