package com.codesync.version.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Commit entity – an immutable snapshot of a project's file tree at a given point in time.
 *
 * Inspired by Git's commit model:
 *  - Each commit stores a human-readable message and author.
 *  - A commit belongs to exactly one Branch.
 *  - A commit records which file snapshots (CommitFile) were included.
 *  - Commits are NEVER updated after creation (append-only history).
 *
 * Relationship summary:
 *   Branch   1──* Commit
 *   Commit   1──* CommitFile
 */
@Entity
@Table(
    name = "commits",
    indexes = {
        @Index(name = "idx_commit_branch",    columnList = "branch_id"),
        @Index(name = "idx_commit_project",   columnList = "project_id"),
        @Index(name = "idx_commit_author",    columnList = "author_id"),
        @Index(name = "idx_commit_created",   columnList = "created_at")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Commit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Short SHA-256 hash (first 8 chars) that uniquely identifies this commit
     * within a project. Computed at creation time from content + timestamp.
     */
    @Column(name = "commit_hash", nullable = false, length = 64, unique = true)
    private String commitHash;

    /** Human-readable commit message (e.g. "Fix NullPointerException in Parser") */
    @Column(nullable = false, length = 500)
    private String message;

    /** ID of the branch this commit belongs to */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    /** Project this commit is associated with (denormalised for fast queries) */
    @Column(name = "project_id", nullable = false)
    private Long projectId;

    /** User who made this commit (from X-User-Id header) */
    @Column(name = "author_id", nullable = false)
    private Long authorId;

    /** File snapshots captured in this commit */
    @OneToMany(mappedBy = "commit", cascade = CascadeType.ALL,
               orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<CommitFile> files = new ArrayList<>();

    /** Optional tag (e.g. "v1.0.0") – null for regular commits */
    @Column(name = "tag", length = 100)
    private String tag;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
