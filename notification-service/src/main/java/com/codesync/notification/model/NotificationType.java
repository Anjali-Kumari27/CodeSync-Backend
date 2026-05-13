package com.codesync.notification.model;

/**
 * Category of a notification event.
 * Used for filtering and icon mapping in the frontend.
 */
public enum NotificationType {
    COMMENT,      // someone commented on a file you own or follow
    MENTION,      // someone @mentioned you in a comment
    VERSION,      // a commit was pushed to a project you are in
    EXECUTION,    // your code execution completed (success or failure)
    COLLAB,       // someone joined/left a collab session on your file
    PROJECT,      // project membership change (added, removed, role changed)
    SYSTEM        // platform-level announcement
}
