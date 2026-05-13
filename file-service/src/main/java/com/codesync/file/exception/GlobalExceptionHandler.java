package com.codesync.file.exception;

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
 * Global Exception Handler for the File Service.
 * Uses Spring 6 RFC 7807 ProblemDetail for consistent error responses.
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
    @ExceptionHandler(DuplicatePathException.class)
    public ProblemDetail handleDuplicatePath(DuplicatePathException ex) {
        log.warn("Duplicate file path: {}", ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        pd.setTitle("Duplicate File Path");
        pd.setType(URI.create("https://codesync.dev/errors/duplicate-path"));
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }

    // ── 410 Gone ───────────────────────────────────────────────────────────
    @ExceptionHandler(FileDeletedExcepion.class)
    public ProblemDetail handleFileDeleted(FileDeletedExcepion ex) {
        log.warn("File is deleted: {}", ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.GONE, ex.getMessage());
        pd.setTitle("File Has Been Deleted");
        pd.setType(URI.create("https://codesync.dev/errors/file-deleted"));
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }

    // ── 400 Invalid Operation ──────────────────────────────────────────────
    @ExceptionHandler(InvalidFileOperationException.class)
    public ProblemDetail handleInvalidOperation(InvalidFileOperationException ex) {
        log.warn("Invalid file operation: {}", ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        pd.setTitle("Invalid File Operation");
        pd.setType(URI.create("https://codesync.dev/errors/invalid-operation"));
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }

    // ── 400 Validation Errors ──────────────────────────────────────────────
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidationErrors(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new HashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(fe.getField(), fe.getDefaultMessage());
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
