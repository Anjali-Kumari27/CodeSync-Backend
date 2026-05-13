package com.codesync.collab.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * WebSocket STOMP message payload for real-time collaboration.
 *
 * Sent by clients to: /app/collab/edit
 * Broadcast by server to: /topic/session/{sessionId}
 *
 * Supports:
 * - text insert/delete/replace
 * - cursor movement
 * - text selection
 * - join / leave presence
 * - typing indicator
 * - save event
 * - kick / end session
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EditMessage {

    /** Session ID */
    private Long sessionId;

    /** User ID */
    private Long userId;

    /** Username */
    private String username;

    /** Profile picture */
    private String avatarUrl;

    /** Unique collaborator color */
    private String color;

    /** Operation type */
    private EditType type;

    /** Character offset from start */
    private Integer offset;

    /** Inserted / replaced text */
    private String text;

    /** Delete length */
    private Integer length;

    /** Timestamp */
    private Long timestamp;

    /** Cursor position */
    private Integer cursorLine;
    private Integer cursorColumn;

    /** Selected text range */
    private Integer selectionStart;
    private Integer selectionEnd;

    /** Typing indicator */
    private Boolean typing;

    public enum EditType {
        INSERT,
        DELETE,
        REPLACE,
        CURSOR,
        SELECTION,
        PRESENCE,
        JOIN,
        LEAVE,
        TYPING,
        SAVE,
        END_SESSION,
        KICK,
        COMMENT
    }
}