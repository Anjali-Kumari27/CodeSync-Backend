package com.codesync.version.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Branch entity – a named line of development within a project (like Git branches).
 *
 * Design:
 *  - Every project starts with a "main" branch created automatically.
 *  - Branches are scoped to one project (projectId).
 *  - Name must be unique per project.
 *  - headCommitId tracks the latest commit on this branch (updated on each commit).
 */
@Entity
@Table(
    name = "branches",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_branch_project_name", columnNames = {"project_id", "name"})
    },
    indexes = {
        @Index(name = "idx_branch_project", columnList = "project_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Branch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Branch name, e.g. "main", "feature/login", "hotfix/payment" */
    @Column(nullable = false, length = 150)
    private String name;

    /** Project this branch belongs to */
    @Column(name = "project_id", nullable = false)
    private Long projectId;

    /** ID of the user who created the branch */
    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    /** ID of the latest commit on this branch (null = no commits yet) */
    @Column(name = "head_commit_id")
    private Long headCommitId;

    /** Whether this is the default/main branch of the project */
    @Column(name = "is_default", nullable = false)
    @Builder.Default
    private boolean defaultBranch = false;

    /** All commits on this branch (ordered by createdAt descending in queries) */
    @OneToMany(mappedBy = "branch", cascade = CascadeType.ALL,
               orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<Commit> commits = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PreUpdate
    protected void onUpdate() { this.updatedAt = LocalDateTime.now(); }
}
