package com.codesync.project.dto;

import com.codesync.project.model.Language;
import com.codesync.project.model.Visibility;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * DTO for partially updating an existing project.
 * All fields are optional – only non-null values are applied.
 */
@Data
public class UpdateProjectRequest {

    @Size(min = 2, max = 100, message = "Project name must be 2–100 characters")
    private String name;

    @Size(max = 1000, message = "Description cannot exceed 1000 characters")
    private String description;

    private Language language;

    private Visibility visibility;
}
