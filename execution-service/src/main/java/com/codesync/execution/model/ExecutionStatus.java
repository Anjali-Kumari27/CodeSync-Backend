package com.codesync.execution.model;

/**
 * Lifecycle status of a code execution request.
 */
public enum ExecutionStatus {
    QUEUED,      // request received, waiting to run
    RUNNING,     // subprocess started
    COMPLETED,   // process exited normally (exit code may be non-zero)
    FAILED,      // internal error (timeout, sandbox error, OOM)
    CANCELLED    // user cancelled before execution started
}
