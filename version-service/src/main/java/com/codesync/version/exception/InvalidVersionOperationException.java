package com.codesync.version.exception;

/** Thrown for invalid version control operations (e.g. committing to a non-existent branch). Maps to HTTP 400. */
public class InvalidVersionOperationException extends RuntimeException {
    public InvalidVersionOperationException(String message) { super(message); }
}
