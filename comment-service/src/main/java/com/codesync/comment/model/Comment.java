package com.codesync.comment.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Comment entity – an inline code comment or review note attached to a file line.
 *
 * Design:
 *  - A comment is anchored to a specific file (fileId) at an optional line number.
 *  - Comments form threads: a root comment has parentId = null;
 *    replies have parentId = root comment's ID.
 *  - Comments can be RESOLVED (closed) by any project member.
 *  - Soft-deleted comments are hidden but kept for audit purposes.
 *
 * Attachment model:
 *   fileId      – ID in the File Service (no FK across services)
 *   projectId   – denormalised for fast project-scoped queries
 *   lineNumber  – null for file-level comments; set for inline line comments
 *   commitHash  – optional, pins the comment to a specific commit snapshot
 */
@Entity
@Table(
    name = "comments",
    indexes = {
        @Index(name = "idx_comment_file",    columnList = "file_id"),
        @Index(name = "idx_comment_project", columnList = "project_id"),
        @Index(name = "idx_comment_author",  columnList = "author_id"),
        @Index(name = "idx_comment_parent",  columnList = "parent_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Comment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** File this comment is attached to (references File Service) */
    @Column(name = "file_id", nullable = false)
    private Long fileId;

    /** Project this comment belongs to (denormalised) */
    @Column(name = "project_id", nullable = false)
    private Long projectId;

    /** Optional: line number for inline code comments (1-indexed). Null = file-level. */
    @Column(name = "line_number")
    private Integer lineNumber;

    /** Optional: column range start for precise highlighting */
    @Column(name = "column_start")
    private Integer columnStart;

    /** Optional: column range end */
    @Column(name = "column_end")
    private Integer columnEnd;

    /** Optional: pin to a specific commit snapshot */
    @Column(name = "commit_hash", length = 64)
    private String commitHash;

    /** The comment text body (supports Markdown) */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    /** Author user ID (from X-User-Id header) */
    @Column(name = "author_id", nullable = false)
    private Long authorId;

    /**
     * Parent comment ID – null for root (thread-starting) comments.
     * Forms a two-level thread hierarchy: root → replies.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Comment parent;

    /** Replies to this comment (only populated for root comments) */
    @OneToMany(mappedBy = "parent", cascade = CascadeType.ALL,
               orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<Comment> replies = new ArrayList<>();

    /** Whether this comment thread has been resolved/closed */
    @Column(nullable = false)
    @Builder.Default
    private boolean resolved = false;

    /** User who resolved this comment (null if not resolved) */
    @Column(name = "resolved_by")
    private Long resolvedBy;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    /** Soft-delete: deleted comments are replaced with "[deleted]" display */
    @Column(nullable = false)
    @Builder.Default
    private boolean deleted = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /** True if the body has been edited after creation */
    @Column(nullable = false)
    @Builder.Default
    private boolean edited = false;

    @OneToMany(mappedBy = "comment", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<CommentHistory> history = new ArrayList<>();

    @PreUpdate
    protected void onUpdate() { this.updatedAt = LocalDateTime.now(); }
}
