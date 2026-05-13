package com.codesync.project.exception;

/**
 * Thrown when a duplicate resource conflict is detected.
 * Maps to HTTP 409 Conflict.
 */
public class DuplicateResourceException extends RuntimeException {

    public DuplicateResourceException(String message) {
        super(message);
    }
}
