package com.codesync.notification.controller;

import com.codesync.notification.dto.*;
import com.codesync.notification.model.NotificationType;
import com.codesync.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST Controller for the Notification Service.
 *
 * Base path: /api/notifications
 * Auth      : X-User-Id header from API Gateway JWT filter.
 *
 * Notification CRUD:
 *   POST   /api/notifications                            – create notification (internal service call)
 *   GET    /api/notifications/{id}                      – get single notification
 *   DELETE /api/notifications/{id}                      – soft-delete
 *
 * Inbox:
 *   GET    /api/notifications/inbox                     – full inbox (paginated)
 *   GET    /api/notifications/inbox/unread              – unread only (paginated)
 *   GET    /api/notifications/inbox/type/{type}         – by type (paginated)
 *   GET    /api/notifications/inbox/summary             – badge count (unread + total)
 *
 * Mark as read:
 *   PATCH  /api/notifications/{id}/read                 – mark one as read
 *   PATCH  /api/notifications/read-all                  – mark all as read
 *
 * Preferences:
 *   GET    /api/notifications/preferences               – get preferences
 *   PATCH  /api/notifications/preferences               – update preferences
 */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "In-app and email notifications for CodeSync platform events")
public class NotificationController {

    private final NotificationService notificationService;

    // ═══════════════════════════════════════════════════════════════════════
    // ── CREATE ──────────────────────────────────────────────────────────────
    // ═══════════════════════════════════════════════════════════════════════

    @PostMapping
    @Operation(summary = "Create a notification",
               description = "Called by other microservices to deliver in-app and/or email notifications.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Notification created"),
        @ApiResponse(responseCode = "400", description = "Validation error")
    })
    public ResponseEntity<NotificationResponse> create(
            @Valid @RequestBody CreateNotificationRequest request,
            @RequestParam(value = "recipientEmail", required = false) String recipientEmail,
            @RequestHeader("X-User-Id") Long userId) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(notificationService.createNotification(request, recipientEmail));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a specific notification by ID")
    @ApiResponse(responseCode = "200", description = "Notification found")
    public ResponseEntity<NotificationResponse> getNotification(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long userId) {

        return ResponseEntity.ok(notificationService.getNotification(id, userId));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Soft-delete a notification")
    @ApiResponse(responseCode = "204", description = "Notification deleted")
    public ResponseEntity<Void> deleteNotification(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long userId) {

        notificationService.deleteNotification(id, userId);
        return ResponseEntity.noContent().build();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ── INBOX ───────────────────────────────────────────────────────────────
    // ═══════════════════════════════════════════════════════════════════════

    @GetMapping("/inbox")
    @Operation(summary = "Get full inbox (paginated, newest first)")
    public ResponseEntity<Page<NotificationResponse>> getInbox(
            @Parameter(description = "Page (0-indexed)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size")        @RequestParam(defaultValue = "20") int size,
            @RequestHeader("X-User-Id") Long userId) {

        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(notificationService.getInbox(userId, pageable));
    }

    @GetMapping("/inbox/unread")
    @Operation(summary = "Get unread notifications (paginated)")
    public ResponseEntity<Page<NotificationResponse>> getUnread(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestHeader("X-User-Id") Long userId) {

        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(notificationService.getUnread(userId, pageable));
    }

    @GetMapping("/inbox/type/{type}")
    @Operation(summary = "Get notifications filtered by type (paginated)")
    public ResponseEntity<Page<NotificationResponse>> getByType(
            @PathVariable NotificationType type,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestHeader("X-User-Id") Long userId) {

        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(notificationService.getByType(userId, type, pageable));
    }

    @GetMapping("/inbox/summary")
    @Operation(summary = "Get notification badge summary (unread count + total)")
    public ResponseEntity<NotificationSummary> getSummary(
            @RequestHeader("X-User-Id") Long userId) {

        return ResponseEntity.ok(notificationService.getSummary(userId));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ── MARK AS READ ────────────────────────────────────────────────────────
    // ═══════════════════════════════════════════════════════════════════════

    @PatchMapping("/{id}/read")
    @Operation(summary = "Mark a notification as read")
    @ApiResponse(responseCode = "200", description = "Marked as read")
    public ResponseEntity<NotificationResponse> markAsRead(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long userId) {

        return ResponseEntity.ok(notificationService.markAsRead(id, userId));
    }

    @PatchMapping("/read-all")
    @Operation(summary = "Mark all notifications as read for the current user")
    @ApiResponse(responseCode = "200", description = "Count of notifications marked as read")
    public ResponseEntity<Integer> markAllAsRead(@RequestHeader("X-User-Id") Long userId) {
        return ResponseEntity.ok(notificationService.markAllAsRead(userId));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ── PREFERENCES ─────────────────────────────────────────────────────────
    // ═══════════════════════════════════════════════════════════════════════

    @GetMapping("/preferences")
    @Operation(summary = "Get notification preferences for the current user")
    public ResponseEntity<PreferenceResponse> getPreferences(
            @RequestHeader("X-User-Id") Long userId) {

        return ResponseEntity.ok(notificationService.getPreferences(userId));
    }

    @PatchMapping("/preferences")
    @Operation(summary = "Update notification preferences (partial update)",
               description = "Only provided fields are updated. Omit fields to keep their current values.")
    public ResponseEntity<PreferenceResponse> updatePreferences(
            @RequestBody UpdatePreferenceRequest request,
            @RequestHeader("X-User-Id") Long userId) {

        return ResponseEntity.ok(notificationService.updatePreferences(userId, request));
    }
}
