package com.codesync.execution.exception;

/** Thrown when the execution sandbox is unavailable or times out. Maps to 503. */
public class ExecutionException extends RuntimeException {
    public ExecutionException(String message) { super(message); }
    public ExecutionException(String message, Throwable cause) { super(message, cause); }
}
