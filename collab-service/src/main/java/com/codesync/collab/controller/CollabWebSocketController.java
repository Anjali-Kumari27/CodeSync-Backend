package com.codesync.collab.controller;

import com.codesync.collab.dto.EditMessage;
import com.codesync.collab.service.CollabService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;


/**
 * STOMP WebSocket Controller for real-time collaborative editing.
 *
 * Clients connect via SockJS to: ws://localhost:8084/ws/collab
 *
 * 
 *   Client → Server (send)         Server → Client (subscribe)    
 * 
 *   /app/collab/edit   =>           /topic/session/{sessionId}      
 *   /app/collab/presence =>         /topic/session/{sessionId}      
 * 
 *
 * The X-User-Id is passed as a STOMP header (set by the frontend
 * client from the JWT token stored in localStorage).
 *
 * Example STOMP payload for an INSERT edit:
 * {
 *   "sessionId": 1,
 *   "type": "INSERT",
 *   "offset": 42,
 *   "text": "Hello",
 *   "timestamp": 1714143600000
 * }
 */

@Controller
@RequiredArgsConstructor
@Slf4j
public class CollabWebSocketController {

    private final CollabService collabService;

    /**
     * Handle real-time edit operations (INSERT, DELETE, REPLACE, CURSOR).
     *
     * Receives from: /app/collab/edit
     * Broadcasts to: /topic/session/{sessionId}
     *
     * userId is extracted from the STOMP X-User-Id header
     * (set by the client after JWT authentication).
     */
    @MessageMapping("/collab/edit")
    public void handleEdit(
            @Payload EditMessage message,
            @Header(value = "X-User-Id", required = false, defaultValue = "0") Long userId) {

        log.debug("Edit received -> session: {}, type: {}, user: {}",
                message.getSessionId(), message.getType(), userId);

        collabService.handleEdit(message, userId);
    }

    /**
     * Handle presence events (user joined/left notifications from the client).
     *
     * Receives from: /app/collab/presence
     * Broadcasts to: /topic/session/{sessionId}
     */
    @MessageMapping("/collab/presence")
    public void handlePresence(
            @Payload EditMessage message,
            @Header(value = "X-User-Id", required = false, defaultValue = "0") Long userId) {

        log.debug("Presence update -> session: {}, user: {}",
                message.getSessionId(), userId);

        collabService.handlePresence(message, userId);
    }

    /**
     * Handle live cursor movement updates.
     *
     * Receives from: /app/collab/cursor
     * Broadcasts cursor line / column so collaborators
     * can see each other's caret position in real time.
     */
    @MessageMapping("/collab/cursor")
    public void handleCursor(
            @Payload EditMessage message,
            @Header(value = "X-User-Id", required = false, defaultValue = "0") Long userId) {

        log.debug("Cursor moved -> session: {}, user: {}",
                message.getSessionId(), userId);

        collabService.handleCursor(message, userId);
    }

    /**
     * Handle text selection changes.
     *
     * Receives from: /app/collab/selection
     * Broadcasts selected text range so other users
     * can view highlighted regions.
     */
    @MessageMapping("/collab/selection")
    public void handleSelection(
            @Payload EditMessage message,
            @Header(value = "X-User-Id", required = false, defaultValue = "0") Long userId) {

        log.debug("Selection changed -> session: {}, user: {}",
                message.getSessionId(), userId);

        collabService.handleSelection(message, userId);
    }

    /**
     * Handle typing indicator updates.
     *
     * Receives from: /app/collab/typing
     * Broadcasts typing state so frontend can show:
     * "User is typing..."
     */
    @MessageMapping("/collab/typing")
    public void handleTyping(
            @Payload EditMessage message,
            @Header(value = "X-User-Id", required = false, defaultValue = "0") Long userId) {

        log.debug("Typing event -> session: {}, user: {}",
                message.getSessionId(), userId);

        collabService.handleTyping(message, userId);
    }

    /**
     * Handle participant heartbeat pings.
     *
     * Receives from: /app/collab/heartbeat
     * Used to keep participant presence alive and
     * detect inactive users for cleanup.
     */
    @MessageMapping("/collab/heartbeat")
    public void handleHeartbeat(
            @Payload EditMessage message,
            @Header(value = "X-User-Id", required = false, defaultValue = "0") Long userId) {

        collabService.handleHeartbeat(message, userId);
    }

    /**
     * Handle file save events during collaboration.
     *
     * Receives from: /app/collab/save
     * Broadcasts save notification to all active participants.
     */
    @MessageMapping("/collab/save")
    public void handleSave(
            @Payload EditMessage message,
            @Header(value = "X-User-Id", required = false, defaultValue = "0") Long userId) {

        log.debug("File saved -> session: {}, user: {}",
                message.getSessionId(), userId);

        collabService.handleSave(message, userId);
    }

    /**
     * Handle live comment events.
     *
     * Receives from: /app/collab/comment
     * Broadcasts to all connected participants so they can reload comments.
     */
    @MessageMapping("/collab/comment")
    public void handleComment(
            @Payload EditMessage message,
            @Header(value = "X-User-Id", required = false, defaultValue = "0") Long userId) {

        log.debug("Comment added -> session: {}, user: {}",
                message.getSessionId(), userId);

        collabService.handleComment(message, userId);
    }
}