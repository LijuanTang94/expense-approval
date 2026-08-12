package com.sandy.expense.web.error;

import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Turns exceptions into a uniform {code, message, timestamp, [fields]} JSON envelope. */
@RestControllerAdvice
public class GlobalExceptionHandler {

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
}
