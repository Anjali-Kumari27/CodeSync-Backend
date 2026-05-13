package com.codesync.file.dto;

import com.codesync.file.model.FileType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Request DTO for creating a new file or directory.
 */
@Data
public class CreateFileRequest {

    @NotNull(message = "Project ID is required")
    private Long projectId;

    /**
     * Full path relative to the project root, e.g. "src/main/App.java".
     * Must not start with '/', use forward slashes only.
     */
    @NotBlank(message = "File path is required")
    @Size(max = 500, message = "File path must not exceed 500 characters")
    @Pattern(
        regexp = "^[^/].*",
        message = "File path must not start with a forward slash"
    )
    private String filePath;

    @NotNull(message = "File type is required (FILE or DIRECTORY)")
    private FileType fileType;

    /** Optional parent directory ID. Null = place at project root. */
    private Long parentId;

    /**
     * Initial content for FILE nodes (ignored for DIRECTORY).
     * Empty string is valid (blank file).
     */
    private String content;

    /**
     * MIME / language type hint, e.g. "text/x-java".
     * If omitted, the service will infer it from the file extension.
     */
    private String mimeType;
}
