package com.codesync.project.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Global Exception Handler for the Project Service.
 *
 * Produces RFC 7807 ProblemDetail responses for all handled exceptions.
 * All error payloads follow the same structure:
 * {
 *   "type":      "https://codesync.dev/errors/<error-slug>",
 *   "title":     "Human Readable Title",
 *   "status":    <HTTP status code>,
 *   "detail":    "Full error message",
 *   "timestamp": "2026-04-26T...",
 *   "fieldErrors": { ... }   // only for validation errors
 * }
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // ── 404 Not Found ──────────────────────────────────────────────────────
    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleResourceNotFound(ResourceNotFoundException ex) {
        log.warn("Resource not found: {}", ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        pd.setTitle("Resource Not Found");
        pd.setType(URI.create("https://codesync.dev/errors/resource-not-found"));
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }

    // ── 403 Forbidden ──────────────────────────────────────────────────────
    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException ex) {
        log.warn("Access denied: {}", ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
        pd.setTitle("Access Denied");
        pd.setType(URI.create("https://codesync.dev/errors/access-denied"));
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }

    // ── 409 Conflict ───────────────────────────────────────────────────────
    @ExceptionHandler(DuplicateResourceException.class)
    public ProblemDetail handleDuplicateResource(DuplicateResourceException ex) {
        log.warn("Duplicate resource: {}", ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        pd.setTitle("Duplicate Resource");
        pd.setType(URI.create("https://codesync.dev/errors/duplicate-resource"));
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }

    // ── 400 Validation Errors ──────────────────────────────────────────────
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidationErrors(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(error.getField(), error.getDefaultMessage());
        }
        log.warn("Validation failed: {}", fieldErrors);
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Validation failed");
        pd.setTitle("Invalid Request");
        pd.setType(URI.create("https://codesync.dev/errors/validation-failed"));
        pd.setProperty("timestamp", Instant.now());
        pd.setProperty("fieldErrors", fieldErrors);
        return pd;
    }

    // ── 500 Catch-all ──────────────────────────────────────────────────────
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGenericException(Exception ex) {
        log.error("Unexpected error: {}", ex.getMessage(), ex);
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred. Please try again later.");
        pd.setTitle("Internal Server Error");
        pd.setType(URI.create("https://codesync.dev/errors/internal-error"));
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }
}
