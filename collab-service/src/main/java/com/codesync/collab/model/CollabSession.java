package com.codesync.collab.model;

import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

import java.time.LocalDateTime;

/**
 * CollabSession – tracks an active real-time editing session for a specific file.
 *
 * Design:
 *  - A session is scoped to one file within one project.
 *  - Sessions have a status: ACTIVE (open for new participants) or CLOSED.
 *  - Sessions track how many edit operations occurred (for audit).
 *  - When the last participant leaves, the session is auto-closed.
 *
 * WebSocket flow:
 *  1. Client connects to WS: /ws/collab
 *  2. Client subscribes to: /topic/session/{sessionId}
 *  3. Client sends edits to: /app/collab/edit
 *  4. Server broadcasts edit to all subscribers of that topic.
 */

@Entity
@Table(
        name = "collab_sessions",
        indexes = {
                @Index(name = "idx_session_file", columnList = "file_id"),
                @Index(name = "idx_session_project", columnList = "project_id"),
                @Index(name = "idx_session_status", columnList = "status")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CollabSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Public shareable session UUID
     */
    @Column(nullable = false, unique = true, updatable = false)
    @Builder.Default
    private String sessionKey = UUID.randomUUID().toString();

    /**
     * Session display name
     */
    @Column(nullable = false)
    private String sessionName;

    /**
     * File scope
     */
    @Column(name = "file_id", nullable = false)
    private Long fileId;

    /**
     * Project scope
     */
    @Column(name = "project_id", nullable = false)
    private Long projectId;

    /**
     * Owner
     */
    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "owner_name")
    private String ownerName;

    /**
     * Java / Python / JS ...
     */
    @Column(nullable = false)
    private String language;

    /**
     * Status
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private SessionStatus status = SessionStatus.ACTIVE;

    /**
     * Metrics
     */
    @Builder.Default
    private Long editCount = 0L;

    @Builder.Default
    private Long participantCount = 0L;

    @Builder.Default
    private Integer activeParticipants = 0;

    /**
     * Access
     */
    private String password;

    @Builder.Default
    private Boolean passwordProtected = false;

    private Integer maxParticipants;

    /**
     * Permissions
     */
    @Builder.Default
    private Boolean viewerModeAllowed = true;

    /**
     * Activity tracking
     */
    @Builder.Default
    private LocalDateTime lastActivity = LocalDateTime.now();

    @Builder.Default
    private Boolean autoCleanup = true;

    /**
     * Audit
     */
    @Column(nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime closedAt;
}