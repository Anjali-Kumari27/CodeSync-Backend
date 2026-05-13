package com.codesync.comment.exception;

/** Thrown for invalid operations (e.g. replying to a reply, reacting twice). Maps to 400. */
public class InvalidCommentOperationException extends RuntimeException {
    public InvalidCommentOperationException(String message) { super(message); }
}
