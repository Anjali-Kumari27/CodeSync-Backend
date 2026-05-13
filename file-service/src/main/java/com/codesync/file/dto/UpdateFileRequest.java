package com.codesync.file.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Request DTO for updating an existing file's content or path.
 * All fields are optional — only non-null values are applied (PATCH semantics).
 */
@Data
public class UpdateFileRequest {

    /**
     * New file content (for FILE nodes only).
     * Pass null to leave content unchanged.
     */
    private String content;

    /**
     * New logical path (rename / move).
     * Must be unique within the project.
     */
    @Size(max = 500, message = "File path must not exceed 500 characters")
    private String filePath;

    /**
     * New file name (just the leaf name, e.g. "Renamed.java").
     * If filePath is also set, this is derived automatically.
     */
    @Size(max = 255)
    private String fileName;

    /** Override the MIME type */
    private String mimeType;

    /** Move to a different parent directory (null = move to root) */
    private Long parentId;
}
