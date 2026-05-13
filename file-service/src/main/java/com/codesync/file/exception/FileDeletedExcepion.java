package com.codesync.file.exception;

/**
 * Thrown when an operation targets a deleted (soft-deleted) file.
 * Maps to HTTP 410 Gone.
 */
public class FileDeletedExcepion extends RuntimeException {
    public FileDeletedExcepion(String message) { super(message); }
}
