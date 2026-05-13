package com.codesync.comment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Request DTO for adding an emoji reaction to a comment.
 */
@Data
public class AddReactionRequest {

    @NotNull(message = "Comment ID is required")
    private Long commentId;

    @NotBlank(message = "Emoji is required")
    @Size(min = 1, max = 20, message = "Emoji must be 1–20 characters")
    private String emoji;
}
