package com.codesync.collab.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * HTTP REST request to create/join a collaboration session.
 */
@Data
public class CreateSessionRequest {

    @NotNull(message = "File ID is required")
    private Long fileId;

    @NotNull(message = "Project ID is required")
    private Long projectId;

    /** Display name for session */
    @NotBlank(message = "Session name is required")
    private String sessionName;

    /** Programming language */
    @NotBlank(message = "Language is required")
    private String language;

    /** Optional password */
    private String password;

    /** Maximum allowed participants */
    private Integer maxParticipants;

    /** Allow read-only viewers */
    private Boolean viewerModeAllowed = true;
}