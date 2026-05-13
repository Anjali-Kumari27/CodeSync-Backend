package com.codesync.version.model;

/**
 * Indicates what happened to a file in a specific commit.
 */
public enum ChangeType {
    ADDED,       // file was newly created
    MODIFIED,    // file content or metadata changed
    DELETED,     // file was removed from the project
    UNCHANGED    // file was unchanged but included for full snapshot completeness
}
