package com.codesync.file.exception;

/**
 * Thrown when a file operation would result in a duplicate path within a project.
 * Maps to HTTP 409 Conflict.
 */
public class DuplicatePathException extends RuntimeException {
    public DuplicatePathException(String message) { super(message); }
}
