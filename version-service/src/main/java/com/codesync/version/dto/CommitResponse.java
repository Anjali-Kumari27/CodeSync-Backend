package com.codesync.version.dto;

import com.codesync.version.model.ChangeType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Response DTO for a commit — summary or detailed view.
 */
@Data
@Builder
public class CommitResponse {
    private Long id;
    private String commitHash;
    private String message;
    private Long branchId;
    private String branchName;
    private Long projectId;
    private Long authorId;
    private String tag;
    private int fileCount;
    private LocalDateTime createdAt;

    /** File snapshots — included only in detailed single-commit responses */
    private List<CommitFileResponse> files;

    @Data
    @Builder
    public static class CommitFileResponse {
        private Long id;
        private Long fileId;
        private String filePath;
        private String content;      // included only in diff/restore endpoints
        private ChangeType changeType;
    }
}
