package com.codesync.comment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Request DTO for posting a new comment on a file.
 */
@Data
public class CreateCommentRequest {

    @NotNull(message = "File ID is required")
    private Long fileId;

    @NotNull(message = "Project ID is required")
    private Long projectId;

    /** 1-indexed line number for inline comment. Null = file-level comment. */
    @Positive(message = "Line number must be positive")
    private Integer lineNumber;

    /** Column range for precise highlighting (optional) */
    @Positive
    private Integer columnStart;

    @Positive
    private Integer columnEnd;

    /** Optional commit hash to pin the comment to a specific snapshot */
    private String commitHash;

    @NotBlank(message = "Comment body is required")
    @Size(min = 1, max = 10000, message = "Body must be 1–10000 characters")
    private String body;

    /**
     * If set, this comment is a reply to the given root comment.
     * Must be a root comment (no deeply nested threads).
     */
    private Long parentId;
}
