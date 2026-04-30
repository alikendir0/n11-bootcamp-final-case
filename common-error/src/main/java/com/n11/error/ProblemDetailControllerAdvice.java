package com.n11.error;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * Cross-cutting RFC-7807 (problem+json) error mapper for every Boot service.
 *
 * Locked field set per D-09 / .planning/api-contracts.md §7:
 *   type, title, status, detail, instance, correlationId, errors[] (validation only).
 *
 * Sanitization rule: the generic Exception handler MUST NOT echo the raw
 * exception message — it could leak SQL fragments, internal class names, or
 * stack traces. Full exception is logged via SLF4J keyed by the same
 * correlationId; ops correlate through logs.
 */
@RestControllerAdvice
public class ProblemDetailControllerAdvice {

    private static final Logger log = LoggerFactory.getLogger(ProblemDetailControllerAdvice.class);

    public static final String CORRELATION_ID_MDC_KEY = "correlationId";

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex,
                                          HttpServletRequest req) {
        ProblemDetail pd = base(ApiErrorCode.VALIDATION, HttpStatus.BAD_REQUEST,
            "One or more fields failed validation", req);
        List<Map<String, String>> errors = ex.getBindingResult().getFieldErrors().stream()
            .map(fe -> Map.of(
                "field",   fe.getField(),
                "message", fe.getDefaultMessage() == null ? "invalid" : fe.getDefaultMessage()))
            .toList();
        pd.setProperty("errors", errors);
        return pd;
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ProblemDetail handleResponseStatus(ResponseStatusException exception,
                                              HttpServletRequest req) {
        HttpStatus status = HttpStatus.valueOf(exception.getStatusCode().value());
        ApiErrorCode code = mapStatusToCode(status);
        // ResponseStatusException carries a developer-controlled `reason` — safe to surface
        // (unlike a raw exception message). Fall back to the code's default title.
        String detail = exception.getReason() == null ? code.defaultTitle() : exception.getReason();
        return base(code, status, detail, req);
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGeneric(Exception exception, HttpServletRequest req) {
        // SECURITY: never echo the raw exception message — anti-leak rule per RESEARCH §14 V7.
        // The full exception is logged via SLF4J keyed by correlationId; the response body
        // carries only the sanitized title + correlationId so ops can correlate via logs.
        log.error("Unhandled exception at {} (correlationId={})",
                req.getRequestURI(), MDC.get(CORRELATION_ID_MDC_KEY), exception);
        return base(ApiErrorCode.INTERNAL, HttpStatus.INTERNAL_SERVER_ERROR,
            "An unexpected error occurred", req);
    }

    // ----- helpers -----

    private ProblemDetail base(ApiErrorCode code, HttpStatus status, String detail,
                               HttpServletRequest req) {
        ProblemDetail pd = ProblemDetail.forStatus(status);
        pd.setType(code.typeUri());
        pd.setTitle(code.defaultTitle());
        pd.setDetail(detail);
        pd.setInstance(URI.create(req.getRequestURI()));
        // correlationId is ALWAYS present per D-09 — fall back to a placeholder so the
        // contract holds even if the upstream filter didn't run for some reason.
        String cid = MDC.get(CORRELATION_ID_MDC_KEY);
        pd.setProperty("correlationId", cid == null ? "unknown" : cid);
        return pd;
    }

    private ApiErrorCode mapStatusToCode(HttpStatus status) {
        return switch (status) {
            case BAD_REQUEST              -> ApiErrorCode.VALIDATION;
            case NOT_FOUND                -> ApiErrorCode.NOT_FOUND;
            case CONFLICT                 -> ApiErrorCode.CONFLICT;
            case UNAUTHORIZED, FORBIDDEN  -> ApiErrorCode.UNAUTHORIZED;
            default                       -> ApiErrorCode.INTERNAL;
        };
    }
}
