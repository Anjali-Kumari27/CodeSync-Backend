package com.codesync.execution.exception;

/** Thrown when the submitted code exceeds the allowed time limit. Maps to 408. */
public class ExecutionTimeoutException extends RuntimeException {
    public ExecutionTimeoutException(String message) { super(message); }
}
