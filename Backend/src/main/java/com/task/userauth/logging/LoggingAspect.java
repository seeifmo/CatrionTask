package com.task.userauth.logging;

import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Cross-cutting logging for controllers and services, so the business code stays free of log
 * boilerplate.
 *
 * <ul>
 *   <li>DEBUG: method entry with its arguments, and exit with the duration.</li>
 *   <li>INFO: one line per controller call with its duration.</li>
 *   <li>WARN: any call slower than {@value #SLOW_CALL_MS} ms.</li>
 * </ul>
 *
 * <p>Safety rules:
 * <ul>
 *   <li>Return values are never logged (they include tokens and personal data).</li>
 *   <li>Tokens, JWTs and servlet objects are logged by type only.</li>
 *   <li>Request records mask passwords in their {@code toString()}.</li>
 *   <li>Every value has CR/LF stripped and is truncated, which prevents log injection.</li>
 *   <li>Exceptions are logged only at DEBUG here. {@code GlobalExceptionHandler} decides their real
 *       severity, so nothing is logged twice.</li>
 * </ul>
 */
@Aspect
@Component
public class LoggingAspect {

    static final long SLOW_CALL_MS = 500;
    private static final int MAX_ARG_LENGTH = 200;

    @Pointcut("within(@org.springframework.web.bind.annotation.RestController *)")
    void controllers() {
    }

    @Pointcut("within(@org.springframework.stereotype.Service *)")
    void services() {
    }

    @Around("controllers()")
    public Object logController(ProceedingJoinPoint pjp) throws Throwable {
        return proceedAndLog(pjp, true);
    }

    @Around("services()")
    public Object logService(ProceedingJoinPoint pjp) throws Throwable {
        return proceedAndLog(pjp, false);
    }

    private Object proceedAndLog(ProceedingJoinPoint pjp, boolean controller) throws Throwable {
        Logger log = LoggerFactory.getLogger(pjp.getSignature().getDeclaringType());
        String method = pjp.getSignature().getName();
        if (log.isDebugEnabled()) {
            log.debug("-> {}({})", method, describe(pjp.getArgs()));
        }
        long start = System.nanoTime();
        try {
            Object result = pjp.proceed();
            long ms = elapsedMs(start);
            if (ms >= SLOW_CALL_MS) {
                log.warn("<- {} slow: {} ms", method, ms);
            } else if (controller) {
                log.info("<- {} {} ms", method, ms);
            } else {
                log.debug("<- {} {} ms", method, ms);
            }
            return result;
        } catch (Throwable ex) {
            log.debug("<- {} threw {} after {} ms", method, ex.getClass().getSimpleName(), elapsedMs(start));
            throw ex;
        }
    }

    static String describe(Object[] args) {
        return Arrays.stream(args).map(LoggingAspect::describe).collect(Collectors.joining(", "));
    }

    private static String describe(Object arg) {
        if (arg == null) {
            return "null";
        }
        if (arg instanceof Jwt jwt) {
            return "Jwt[sub=" + sanitize(jwt.getSubject()) + "]";
        }
        if (arg instanceof Authentication || arg instanceof ServletRequest || arg instanceof ServletResponse) {
            return arg.getClass().getSimpleName();
        }
        return sanitize(String.valueOf(arg));
    }

    private static String sanitize(String value) {
        String oneLine = value.replaceAll("[\\r\\n\\t]", "_");
        return oneLine.length() > MAX_ARG_LENGTH ? oneLine.substring(0, MAX_ARG_LENGTH) + "..." : oneLine;
    }

    private static long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
