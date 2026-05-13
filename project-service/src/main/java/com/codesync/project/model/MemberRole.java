package com.codesync.project.model;

/**
 * Role a member holds within a project.
 */
public enum MemberRole {
    GUEST,   // read-only
    DEVELOPER,   // edit files + comments
    ADMIN     // manage membership
}
