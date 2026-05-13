package com.codesync.collab.exception;

/** Thrown when trying to join/edit a CLOSED session. Maps to 400. */
public class SessionNotActiveException extends RuntimeException {
    public SessionNotActiveException(String message) { super(message); }
}
