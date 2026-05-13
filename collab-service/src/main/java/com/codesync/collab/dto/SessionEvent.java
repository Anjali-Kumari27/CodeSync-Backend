package com.codesync.collab.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Server-to-client notification broadcast over WebSocket when session state changes
 * (participant joined, left, session closed, etc.).
 */

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionEvent {

    private Long sessionId;

    private EventType eventType;

    /**
     * Triggered by
     */
    private Long userId;
    private String username;
    private String avatarUrl;
    private String color;

    /**
     * Presence
     */
    private Integer activeCount;
    private List<Long> participants;

    /**
     * Cursor / selection
     */
    private Integer cursorLine;
    private Integer cursorColumn;
    private Integer selectionStart;
    private Integer selectionEnd;

    /**
     * Typing state
     */
    private Boolean typing;

    /**
     * Message
     */
    private String message;

    /**
     * Event time
     */
    private LocalDateTime timestamp;

    public enum EventType {
        USER_JOINED,
        USER_LEFT,
        USER_TYPING,
        USER_STOPPED_TYPING,
        CURSOR_MOVED,
        SELECTION_CHANGED,
        EDIT_APPLIED,
        FILE_SAVED,
        PARTICIPANT_KICKED,
        SESSION_CLOSED,
        SESSION_IDLE_TIMEOUT,
        HEARTBEAT
    }
}