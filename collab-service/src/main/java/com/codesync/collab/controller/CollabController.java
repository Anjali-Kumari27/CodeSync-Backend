package com.codesync.collab.controller;

import com.codesync.collab.dto.*;
import com.codesync.collab.service.CollabService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST Controller for session management (non-real-time operations).
 *
 * Base path: /api/collab
 *
 * POST   /api/collab/sessions               – create or join session
 * GET    /api/collab/sessions/{id}          – get session info
 * GET    /api/collab/sessions/{id}/participants – get active participants
 * GET    /api/collab/sessions/file/{fileId} – get active session for file
 * GET    /api/collab/sessions/project/{id}  – list active sessions in project
 * PATCH  /api/collab/sessions/{id}/close    – close session
 * DELETE /api/collab/sessions/{id}/leave    – leave session
 */
@RestController
@RequestMapping("/api/collab")
@RequiredArgsConstructor
@Tag(name = "Collab Sessions", description = "Collaboration session management and participant tracking")
public class CollabController {

    private final CollabService collabService;

    @PostMapping("/sessions")
    @Operation(summary = "Create or join a collaboration session for a file",
               description = "Returns existing active session if one exists, otherwise creates a new one.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Session created or joined"),
        @ApiResponse(responseCode = "400", description = "Validation error")
    })
    public ResponseEntity<SessionResponse> createOrJoinSession(
            @Valid @RequestBody CreateSessionRequest request,
            @RequestHeader("X-User-Id") Long userId) {

        SessionResponse response = collabService.createOrJoinSession(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/sessions/{id}")
    @Operation(summary = "Get session info by ID")
    @ApiResponse(responseCode = "200", description = "Session found")
    public ResponseEntity<SessionResponse> getSession(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long userId) {

        return ResponseEntity.ok(collabService.getSession(id));
    }

    @GetMapping("/sessions/{id}/participants")
    @Operation(summary = "Get currently active participant IDs in a session")
    public ResponseEntity<List<Long>> getActiveParticipants(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long userId) {

        return ResponseEntity.ok(collabService.getActiveParticipants(id));
    }

    @GetMapping("/sessions/file/{fileId}")
    @Operation(summary = "Get the active collaboration session for a specific file")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Active session found"),
        @ApiResponse(responseCode = "404", description = "No active session for this file")
    })
    public ResponseEntity<SessionResponse> getActiveSessionForFile(
            @PathVariable Long fileId,
            @RequestHeader("X-User-Id") Long userId) {

        return ResponseEntity.ok(collabService.getActiveSessionForFile(fileId));
    }

    @GetMapping("/sessions/project/{projectId}")
    @Operation(summary = "List all active sessions in a project")
    public ResponseEntity<List<SessionResponse>> getProjectActiveSessions(
            @PathVariable Long projectId,
            @RequestHeader("X-User-Id") Long userId) {

        return ResponseEntity.ok(collabService.getProjectActiveSessions(projectId));
    }

    @PatchMapping("/sessions/{id}/close")
    @Operation(summary = "Close a session",
               description = "Marks the session as CLOSED and notifies all participants via WebSocket.")
    @ApiResponse(responseCode = "200", description = "Session closed")
    public ResponseEntity<SessionResponse> closeSession(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long userId) {

        return ResponseEntity.ok(collabService.closeSession(id, userId));
    }

    @DeleteMapping("/sessions/{id}/leave")
    @Operation(summary = "Leave a session",
               description = "Records the user's departure. Auto-closes session if no participants remain.")
    @ApiResponse(responseCode = "204", description = "Left session")
    public ResponseEntity<Void> leaveSession(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long userId) {

        collabService.leaveSession(id, userId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/sessions/{id}/kick/{targetUserId}")
    @Operation(summary = "Kick a participant",
               description = "Only the session creator can kick. The user will be removed from the session.")
    @ApiResponse(responseCode = "204", description = "User kicked")
    public ResponseEntity<Void> kickParticipant(
            @PathVariable Long id,
            @PathVariable Long targetUserId,
            @RequestHeader("X-User-Id") Long userId) {

        collabService.kickParticipant(id, targetUserId, userId);
        return ResponseEntity.noContent().build();
    }
}
