package com.codesync.version.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Response DTO for a branch.
 */
@Data
@Builder
public class BranchResponse {
    private Long id;
    private String name;
    private Long projectId;
    private Long createdBy;
    private Long headCommitId;
    private boolean defaultBranch;
    private long commitCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
