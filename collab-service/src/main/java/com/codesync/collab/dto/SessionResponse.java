package com.codesync.collab.dto;

import com.codesync.collab.model.SessionStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Response DTO for a collaboration session.
 */
@Data
@Builder
public class SessionResponse {

    private Long id;

    private String sessionKey;

    private String sessionName;

    private Long fileId;

    private Long projectId;

    private Long createdBy;

    private String ownerName;

    private String language;

    private SessionStatus status;

    private Long editCount;

    private Long participantCount;

    private Integer activeParticipants;

    private List<Long> activeParticipantIds;

    private Integer maxParticipants;

    private boolean hasPassword;

    private Boolean viewerModeAllowed;

    private LocalDateTime lastActivity;

    private LocalDateTime createdAt;

    private LocalDateTime closedAt;
}