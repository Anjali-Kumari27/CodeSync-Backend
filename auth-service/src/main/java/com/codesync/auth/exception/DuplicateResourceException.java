package com.codesync.auth.exception;

/**
 * Thrown when a user tries to register with a duplicate email or username.
 */
public class DuplicateResourceException extends RuntimeException {

    public DuplicateResourceException(String message) {
        super(message);
    }
}
