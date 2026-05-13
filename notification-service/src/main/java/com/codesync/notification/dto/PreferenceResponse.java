package com.codesync.notification.dto;

import com.codesync.notification.model.NotificationPreference;
import lombok.Builder;
import lombok.Data;

/**
 * Response DTO for user notification preferences.
 */
@Data
@Builder
public class PreferenceResponse {
    private Long userId;
    private boolean commentInApp;
    private boolean commentEmail;
    private boolean mentionInApp;
    private boolean mentionEmail;
    private boolean versionInApp;
    private boolean versionEmail;
    private boolean executionInApp;
    private boolean executionEmail;
    private boolean collabInApp;
    private boolean collabEmail;
    private boolean projectInApp;
    private boolean projectEmail;
    private boolean systemInApp;
    private boolean systemEmail;

    public static PreferenceResponse from(NotificationPreference p) {
        return PreferenceResponse.builder()
                .userId(p.getUserId())
                .commentInApp(p.isCommentInApp()).commentEmail(p.isCommentEmail())
                .mentionInApp(p.isMentionInApp()).mentionEmail(p.isMentionEmail())
                .versionInApp(p.isVersionInApp()).versionEmail(p.isVersionEmail())
                .executionInApp(p.isExecutionInApp()).executionEmail(p.isExecutionEmail())
                .collabInApp(p.isCollabInApp()).collabEmail(p.isCollabEmail())
                .projectInApp(p.isProjectInApp()).projectEmail(p.isProjectEmail())
                .systemInApp(p.isSystemInApp()).systemEmail(p.isSystemEmail())
                .build();
    }
}
