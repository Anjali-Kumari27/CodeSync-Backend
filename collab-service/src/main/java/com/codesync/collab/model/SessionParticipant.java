package com.codesync.collab.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * SessionParticipant – records which users have joined a collaboration session.
 *
 * Each row represents one user's participation in one session.
 * - joinedAt / leftAt tracks connection windows.
 * - A user can join the same session multiple times (reconnects) – each creates a new row.
 * - The most recent row with leftAt = null means the user is currently active.
 */

@Entity
@Table(
        name = "session_participants",
        indexes = {
                @Index(name = "idx_participant_session", columnList = "session_id"),
                @Index(name = "idx_participant_user", columnList = "user_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SessionParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /* session */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private CollabSession session;

    /* user */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String username;

    private String avatarUrl;

    /**
     * HOST / EDITOR / VIEWER
     */
    @Column(nullable = false)
    @Builder.Default
    private String role = "EDITOR";

    /**
     * Unique color for cursor highlight
     */
    @Column(nullable = false)
    private String color;

    /**
     * Cursor state
     */
    @Builder.Default
    private Integer cursorLine = 1;

    @Builder.Default
    private Integer cursorColumn = 1;

    /**
     * Selection
     */
    private Integer selectionStart;
    private Integer selectionEnd;

    /**
     * Typing state
     */
    @Builder.Default
    private Boolean typing = false;

    /**
     * Active state
     */
    @Builder.Default
    private Boolean active = true;

    /**
     * Presence heartbeat
     */
    @Column(name = "last_heartbeat")
    @Builder.Default
    private LocalDateTime lastHeartbeat = LocalDateTime.now();

    /**
     * Join / leave tracking
     */
    @Column(name = "joined_at", nullable = false)
    @Builder.Default
    private LocalDateTime joinedAt = LocalDateTime.now();

    @Column(name = "left_at")
    private LocalDateTime leftAt;
}