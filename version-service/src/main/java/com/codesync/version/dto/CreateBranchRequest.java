package com.codesync.version.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Request to create a new branch within a project.
 */
@Data
public class CreateBranchRequest {

    @NotNull(message = "Project ID is required")
    private Long projectId;

    @NotBlank(message = "Branch name is required")
    @Size(min = 1, max = 150, message = "Branch name must be 1–150 characters")
    private String name;

    /**
     * Optional: branch from a specific commit hash.
     * If null, branches from the HEAD of the project's default branch.
     */
    private String fromCommitHash;
}
