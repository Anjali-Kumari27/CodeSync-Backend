package com.codesync.version.model;

import jakarta.persistence.*;
import lombok.*;

/**
 * CommitFile – an immutable snapshot of a single file's content at commit time.
 *
 * Each CommitFile captures:
 *  - The file's path (as it was at commit time)
 *  - The file's full content (stored as LONGTEXT)
 *  - The change type: ADDED, MODIFIED, DELETED, UNCHANGED
 *
 * This gives us a full diff capability:
 *  - Compare CommitFile.content between two commits for the same filePath.
 *  - changeType tells us what happened to this file in this commit.
 *
 * Files are stored PER COMMIT (not delta-compressed in this version).
 * For production use, replace content storage with a content-addressable
 * blob store (S3 + SHA hash deduplication).
 */
@Entity
@Table(
    name = "commit_files",
    indexes = {
        @Index(name = "idx_cf_commit",    columnList = "commit_id"),
        @Index(name = "idx_cf_file",      columnList = "file_id"),
        @Index(name = "idx_cf_path",      columnList = "commit_id, file_path")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommitFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Parent commit */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "commit_id", nullable = false)
    private Commit commit;

    /**
     * ID of the file in the File Service (no FK across services).
     * Null if the file was deleted.
     */
    @Column(name = "file_id")
    private Long fileId;

    /** File path at commit time (preserved even if the file is later renamed) */
    @Column(name = "file_path", nullable = false, length = 500)
    private String filePath;

    /** Full file content snapshot at commit time */
    @Column(columnDefinition = "LONGTEXT")
    private String content;

    /** What happened to this file in this commit */
    @Enumerated(EnumType.STRING)
    @Column(name = "change_type", nullable = false, length = 20)
    private ChangeType changeType;
}
