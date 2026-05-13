package com.codesync.comment.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Response DTO for a comment.
 * Includes replies and emoji reaction counts.
 */
@Data
@Builder
public class CommentResponse {

    private Long id;
    private Long fileId;
    private Long projectId;
    private Integer lineNumber;
    private Integer columnStart;
    private Integer columnEnd;
    private String commitHash;
    private String body;
    private Long authorId;
    private Long parentId;
    private boolean resolved;
    private Long resolvedBy;
    private LocalDateTime resolvedAt;
    private boolean deleted;
    private boolean edited;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * Populated for root comments in thread view.
     * Empty list for replies.
     */
    private List<CommentResponse> replies;

    /**
     * Emoji → count map, e.g. {"👍": 3, "❤️": 1}
     * Populated on demand (detailed view only).
     */
    private Map<String, Long> reactions;
}
