package com.codesync.project.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateNotificationRequest {

    private Long recipientId;   // who receives notification
    private Long actorId;       // who triggered event

    private String type;        // PROJECT / COMMENT / EXECUTION / etc

    private String title;
    private String message;
    private String actionUrl;
    private Long referenceId;
    private Long projectId;
}