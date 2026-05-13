package com.codesync.notification.dto;

import com.codesync.notification.model.NotificationType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Response DTO for a notification.
 */
@Data
@Builder
public class NotificationResponse {
    private Long id;
    private Long recipientId;
    private Long actorId;
    private NotificationType type;
    private String title;
    private String message;
    private String actionUrl;
    private Long referenceId;
    private Long projectId;
    private boolean read;
    private LocalDateTime readAt;
    private boolean emailSent;
    private LocalDateTime createdAt;
}
