package com.codesync.comment.exception;

/** Thrown when a non-author/non-admin tries to edit or delete another user's comment. Maps to 403. */
public class AccessDeniedException extends RuntimeException {
    public AccessDeniedException(String message) { super(message); }
}
