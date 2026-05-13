package com.codesync.auth.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Global Exception Handler for the Auth Service.
 *
 * Uses RFC 7807 ProblemDetail (built-in Spring 6 / Boot 3) for consistent
 * error responses across all endpoints.
 *
 * Standard error payload:
 * {
 *   "type":    "https://codesync.dev/errors/resource-not-found",
 *   "title":   "Resource Not Found",
 *   "status":  404,
 *   "detail":  "User not found with email: 'test@mail.com'",
 *   "instance": "/api/auth/login"
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

    // ── 401 Unauthorized ───────────────────────────────────────────────────
    @ExceptionHandler(BadCredentialsException.class)
    public ProblemDetail handleBadCredentials(BadCredentialsException ex) {
        log.warn("Bad credentials: {}", ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        pd.setTitle("Authentication Failed");
        pd.setType(URI.create("https://codesync.dev/errors/authentication-failed"));
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

    // ── 500 Internal Server Error ──────────────────────────────────────────
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
