package com.codesync.version.dto;

import com.codesync.version.model.ChangeType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * Request to create a new commit on a branch.
 *
 * The caller supplies:
 *  - branchId   – which branch to commit to
 *  - message    – commit message
 *  - files      – list of file snapshots captured at commit time
 *  - tag        – optional version tag (e.g. "v1.0.0")
 */
@Data
public class CreateCommitRequest {

    @NotNull(message = "Branch ID is required")
    private Long branchId;

    @NotBlank(message = "Commit message is required")
    @Size(min = 1, max = 500, message = "Commit message must be 1–500 characters")
    private String message;

    /** Optional semantic version tag */
    @Size(max = 100)
    private String tag;

    /** Snapshot of each file included in this commit */
    @NotNull(message = "File snapshots are required")
    @Valid
    private List<CommitFileEntry> files;

    /** DTO for a single file snapshot within a commit */
    @Data
    public static class CommitFileEntry {

        /** File ID in the File Service */
        private Long fileId;

        @NotBlank(message = "File path is required")
        @Size(max = 500)
        private String filePath;

        /** Full file content at commit time */
        private String content;

        @NotNull(message = "Change type is required")
        private ChangeType changeType;
    }
}
