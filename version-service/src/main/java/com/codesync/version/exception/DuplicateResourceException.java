package com.codesync.version.exception;

/** Thrown when a branch or commit name/hash already exists. Maps to HTTP 409. */
public class DuplicateResourceException extends RuntimeException {
    public DuplicateResourceException(String message) { super(message); }
}
