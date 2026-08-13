package com.sandy.expense.web.error;

import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Turns exceptions into a uniform {code, message, timestamp, [fields]} JSON envelope. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static Map<String, Object> body(String code, String message) {
        return Map.of("code", code, "message", message, "timestamp", Instant.now().toString());
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, Object>> handleApi(ApiException ex) {
        return ResponseEntity.status(ex.getStatus()).body(body(ex.getCode(), ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fields =
                ex.getBindingResult().getFieldErrors().stream()
                        .collect(
                                Collectors.toMap(
                                        f -> f.getField(),
                                        f -> f.getDefaultMessage() == null ? "invalid" : f.getDefaultMessage(),
                                        (a, b) -> a));
        Map<String, Object> b =
                Map.of(
                        "code", "VALIDATION_ERROR",
                        "message", "Request validation failed",
                        "timestamp", Instant.now().toString(),
                        "fields", fields);
        return ResponseEntity.badRequest().body(b);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(body("FORBIDDEN", "You do not have permission to perform this action"));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Map<String, Object>> handleAuth(AuthenticationException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(body("UNAUTHORIZED", "Invalid credentials"));
    }

    /**
     * Lost the optimistic-lock race: another approver committed a decision on this request first.
     * 409 tells the client its view is stale and it should reload rather than retry blindly.
     */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<Map<String, Object>> handleConcurrentUpdate(
            ObjectOptimisticLockingFailureException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(
                        body(
                                "CONCURRENT_UPDATE",
                                "Someone else updated this request first — reload and try again"));
    }

    /** Malformed or empty JSON body — a client error (400), not a server fault. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleUnreadable(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest().body(body("MALFORMED_JSON", "Request body is not valid JSON"));
    }

    /** e.g. ?status=BOGUS — an unparseable query/path parameter. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return ResponseEntity.badRequest()
                .body(body("BAD_PARAMETER", "Invalid value for parameter '" + ex.getName() + "'"));
    }

    /** A constraint the bean validation layer didn't catch (e.g. a too-long value). */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleIntegrity(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation", ex);
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(body("DATA_INTEGRITY", "The request conflicts with existing data"));
    }

    /**
     * Catch-all. Without this, anything unhandled falls through to Spring's default error page,
     * which returns a different JSON shape — so the "uniform envelope" guarantee would quietly not
     * hold. Logs the real cause server-side and returns a generic message (no internals leaked).
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(body("INTERNAL_ERROR", "Something went wrong"));
    }
}
