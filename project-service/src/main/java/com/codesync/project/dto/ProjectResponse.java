package com.codesync.project.dto;

import com.codesync.project.model.Language;
import com.codesync.project.model.Visibility;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Response DTO for a project – sent to clients.
 * Never exposes internal JPA structure.
 */
@Data
@Builder
public class ProjectResponse {

    private Long id;
    private String name;
    private String description;
    private Language language;
    private Visibility visibility;
    private Long ownerId;
    private int memberCount;
    private boolean archived;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
