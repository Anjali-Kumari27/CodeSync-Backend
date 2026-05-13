package com.codesync.notification.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Notification – a persistent in-app notification delivered to a specific user.
 *
 * Design:
 *  - Notifications are created by backend services (e.g. when a comment is posted,
 *    a version is committed, or an execution completes).
 *  - Each notification targets exactly one user (recipientId).
 *  - Notifications have a type (COMMENT, VERSION, EXECUTION, COLLAB, SYSTEM)
 *    and an optional referenceId pointing to the triggering entity in another service.
 *  - read/readAt tracks whether the user has acknowledged the notification.
 *  - Soft-delete keeps history intact while hiding from the UI.
 *
 * Email:
 *  - If the user has email notifications enabled, an email is also sent
 *    via the EmailService (backed by JavaMailSender).
 *  - emailSent tracks delivery status.
 */
@Entity
@Table(
    name = "notifications",
    indexes = {
        @Index(name = "idx_notif_recipient",   columnList = "recipient_id"),
        @Index(name = "idx_notif_type",        columnList = "type"),
        @Index(name = "idx_notif_read",        columnList = "recipient_id, is_read"),
        @Index(name = "idx_notif_created",     columnList = "created_at")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** User receiving this notification */
    @Column(name = "recipient_id", nullable = false)
    private Long recipientId;

    /** User or system that triggered the notification (null = system) */
    @Column(name = "actor_id")
    private Long actorId;

    /** Category of notification */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private NotificationType type;

    /** Short human-readable title, e.g. "New comment on Auth.java" */
    @Column(nullable = false, length = 255)
    private String title;

    /** Full notification body / detail message */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    /**
     * Optional deep-link data for the frontend.
     * e.g. "/project/5/file/12#comment-88"
     */
    @Column(name = "action_url", length = 500)
    private String actionUrl;

    /** ID of the triggering entity in the originating service (e.g. commentId, commitId) */
    @Column(name = "reference_id")
    private Long referenceId;

    /** Project scope (for project-level filtering) */
    @Column(name = "project_id")
    private Long projectId;

    /** Whether the user has read/acknowledged this notification */
    @Column(name = "is_read", nullable = false)
    @Builder.Default
    private boolean read = false;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    /** Whether an email was successfully sent for this notification */
    @Column(name = "email_sent", nullable = false)
    @Builder.Default
    private boolean emailSent = false;

    /** Soft-delete */
    @Column(nullable = false)
    @Builder.Default
    private boolean deleted = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
