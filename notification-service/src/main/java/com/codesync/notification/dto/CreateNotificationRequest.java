package com.codesync.notification.dto;

import com.codesync.notification.model.NotificationType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateNotificationRequest {

    @NotNull(message = "Recipient user ID is required")
    private Long recipientId;

    private Long actorId;

    @NotBlank(message = "Notification type is required")
    private String type;

    @NotBlank(message = "Title is required")
    @Size(max = 255)
    private String title;

    @NotBlank(message = "Message is required")
    @Size(max = 5000)
    private String message;

    @Size(max = 500)
    private String actionUrl;

    private Long referenceId;

    private Long projectId;
}