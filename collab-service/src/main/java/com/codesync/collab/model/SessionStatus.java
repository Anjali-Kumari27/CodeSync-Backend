package com.codesync.collab.model;

/**
 * Lifecycle status of a collaboration session.
 */
public enum SessionStatus {
    ACTIVE,   // session is open and accepting new participants
    CLOSED    // session has ended (all participants left or manually closed)
}
