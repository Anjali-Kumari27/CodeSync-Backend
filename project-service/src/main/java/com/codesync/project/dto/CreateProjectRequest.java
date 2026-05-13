package com.codesync.project.dto;

import com.codesync.project.model.Language;
import com.codesync.project.model.Visibility;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * DTO for creating a new project.
 */
@Data
public class CreateProjectRequest {

    @NotBlank(message = "Project name is required")
    @Size(min = 2, max = 100, message = "Project name must be 2–100 characters")
    private String name;

    @Size(max = 1000, message = "Description cannot exceed 1000 characters")
    private String description;

    @NotNull(message = "Language is required")
    private Language language;

    private Visibility visibility = Visibility.PRIVATE;
}
