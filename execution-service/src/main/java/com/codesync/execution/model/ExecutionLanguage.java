package com.codesync.execution.model;

/**
 * Supported programming languages for sandboxed execution.
 *
 * Each language maps to a system command used to compile/run the code.
 * The runner command is defined in {@link com.codesync.execution.service.SandboxRunner}.
 */
public enum ExecutionLanguage {
    JAVA,
    PYTHON,
    JAVASCRIPT,   // Node.js
    TYPESCRIPT,   // ts-node
    C,
    CPP,
    GO,
    RUST,
    KOTLIN,
    BASH
}
