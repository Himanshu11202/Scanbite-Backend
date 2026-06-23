package com.scanbite.backend.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(err -> errors.put(err.getField(), err.getDefaultMessage()));
        return ResponseEntity.badRequest().body(errors);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<?> handleNotFound(ResourceNotFoundException ex) {
        System.err.println(String.format("[%s] [ERROR] Resource not found: %s", Instant.now(), ex.getMessage()));
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<?> handleMalformedJson(HttpMessageNotReadableException ex) {
        System.err.println(String.format("[%s] [ERROR] Malformed JSON request: %s", Instant.now(), ex.getMessage()));
        return ResponseEntity.badRequest().body(Map.of("error", "Malformed JSON request body"));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<?> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String reqType = ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "unknown";
        String msg = String.format("Parameter '%s' should be of type '%s'", ex.getName(), reqType);
        System.err.println(String.format("[%s] [ERROR] Type mismatch: %s", Instant.now(), ex.getMessage()));
        return ResponseEntity.badRequest().body(Map.of("error", msg));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<?> handleAccessDenied(AccessDeniedException ex) {
        System.err.println(String.format("[%s] [ERROR] Access denied: %s", Instant.now(), ex.getMessage()));
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<?> handleIllegalArgument(IllegalArgumentException ex) {
        System.err.println(String.format("[%s] [ERROR] Invalid argument: %s", Instant.now(), ex.getMessage()));
        return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleAny(Exception ex) {
        System.err.print(String.format("[%s] [ERROR] Unexpected server exception: ", Instant.now()));
        ex.printStackTrace();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "An unexpected error occurred. Please contact support."));
    }
}
