package com.codesync.execution.dto;

import com.codesync.execution.model.ExecutionLanguage;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Request DTO for submitting code for execution.
 */
@Data
public class ExecutionRequest {

    @NotNull(message = "Project ID is required")
    private Long projectId;

    /** Optional: file ID from the File Service (for audit tracing) */
    private Long fileId;

    @NotBlank(message = "Code is required")
    @Size(min = 1, max = 100000, message = "Code must be 1–100,000 characters")
    private String code;

    @NotNull(message = "Language is required")
    private ExecutionLanguage language;

    /** Optional stdin to pipe to the program */
    @Size(max = 10000, message = "Stdin must be at most 10,000 characters")
    private String stdin;
}
