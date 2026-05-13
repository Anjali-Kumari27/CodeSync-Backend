package com.codesync.comment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Request DTO for editing an existing comment's body.
 */
@Data
public class UpdateCommentRequest {

    @NotBlank(message = "Comment body is required")
    @Size(min = 1, max = 10000, message = "Body must be 1–10000 characters")
    private String body;
}
