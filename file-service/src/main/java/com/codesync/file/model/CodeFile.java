package com.codesync.file.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * CodeFile entity – represents a single source file or a directory node
 * within a project's file tree.
 *
 * Design decisions:
 *  - A file WITHOUT a parent is at the project root.
 *  - A directory has type = DIRECTORY and content = null.
 *  - Content is stored as TEXT in MySQL (max ~65 KB per file).
 *    For larger files, replace with LONGTEXT or external blob storage.
 *  - Files are soft-deleted (deleted = true) so version history is kept.
 */
@Entity
@Table(
    name = "files",
    indexes = {
        @Index(name = "idx_file_project",  columnList = "project_id"),
        @Index(name = "idx_file_parent",   columnList = "parent_id"),
        @Index(name = "idx_file_path",     columnList = "project_id, file_path", unique = true)
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CodeFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Logical full path within the project, e.g. "src/main/App.java".
     * Must be unique per project.
     */
    @Column(name = "file_path", nullable = false, length = 500)
    private String filePath;

    /** Just the filename/directory name, e.g. "App.java" */
    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    /** FILE or DIRECTORY */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private FileType fileType = FileType.FILE;

    /**
     * Nullable for directories.
     * Stored as LONGTEXT to support files up to ~4 GB (MySQL limit).
     */
    @Column(columnDefinition = "LONGTEXT")
    private String content;

    /**
     * MIME / language type for syntax highlighting, e.g. "text/x-java", "text/x-python".
     * Null for directories.
     */
    @Column(name = "mime_type", length = 100)
    private String mimeType;

    /** File size in bytes (0 for directories, computed from content). */
    @Column(name = "size_bytes")
    @Builder.Default
    private Long sizeBytes = 0L;

    /**
     * Reference to the project this file belongs to.
     * Stored as a plain Long (no JPA join across services).
     */
    @Column(name = "project_id", nullable = false)
    private Long projectId;

    /**
     * Self-referencing parent directory.
     * NULL means this node is at the project root.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private CodeFile parent;

    /** ID of the user who created this file (from X-User-Id header) */
    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    /** ID of the last user who modified this file */
    @Column(name = "last_modified_by")
    private Long lastModifiedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /** Soft-delete: deleted files are hidden but retain their history */
    @Column(nullable = false)
    @Builder.Default
    private boolean deleted = false;

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
