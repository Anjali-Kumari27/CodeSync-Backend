package com.codesync.project.exception;

/**
 * Thrown when an operation is attempted by a user who lacks the required permission.
 * Maps to HTTP 403 Forbidden.
 */
public class AccessDeniedException extends RuntimeException {

    public AccessDeniedException(String message) {
        super(message);
    }
}
