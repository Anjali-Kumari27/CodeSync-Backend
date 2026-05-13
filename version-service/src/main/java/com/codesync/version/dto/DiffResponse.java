package com.codesync.version.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Response DTO for a diff between two commits.
 * Shows which files changed and how.
 */
@Data
@Builder
public class DiffResponse {
    private String fromCommitHash;
    private String toCommitHash;
    private int totalChanged;

    private List<FileDiff> diffs;

    @Data
    @Builder
    public static class FileDiff {
        private String filePath;
        private String changeType;   // ADDED | MODIFIED | DELETED
        private String contentBefore;
        private String contentAfter;
    }
}
