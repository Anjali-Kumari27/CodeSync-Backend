package com.codesync.comment.exception;

/** Thrown when a user tries to add the same emoji reaction twice. Maps to 409. */
public class DuplicateReactionException extends RuntimeException {
    public DuplicateReactionException(String message) { super(message); }
}
