package com.codesync.file.exception;

/**
 * Thrown when a file operation is invalid, e.g. writing content to a DIRECTORY node.
 * Maps to HTTP 400 Bad Request.
 */
public class InvalidFileOperationException extends RuntimeException {
    public InvalidFileOperationException(String message) { super(message); }
}
