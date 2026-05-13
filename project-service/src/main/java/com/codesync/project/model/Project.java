package com.codesync.project.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Project entity – the top-level container for all files, collaborators,
 * versions, and comments within CodeSync.
 *
 * One project has:
 *  - One owner (creator)
 *  - Zero-to-many ProjectMembers (collaborators)
 *  - A programming language tag
 *  - A visibility setting (PUBLIC / PRIVATE)
 */
@Entity
@Table(
    name = "projects",
    indexes = {
        @Index(name = "idx_project_owner",    columnList = "owner_id"),
        @Index(name = "idx_project_language", columnList = "language"),
        @Index(name = "idx_project_visibility", columnList = "visibility")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Display name of the project */
    @Column(nullable = false, length = 100)
    private String name;

    /** Optional description / README summary */
    @Column(columnDefinition = "TEXT")
    private String description;

    /** Primary programming language (e.g. JAVA, PYTHON, JAVASCRIPT) */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Language language;

    /** PUBLIC = anyone can view; PRIVATE = members only */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Visibility visibility = Visibility.PRIVATE;

    /** User ID taken from the Auth Service via JWT (X-User-Id header) */
    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    /** Project members (owner is NOT auto-added here; check ownerId) */
    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL,
               orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<ProjectMember> members = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @Column(name = "forked_from_project_id")
    private Long forkedFromProjectId;

    /** Soft-delete flag: deleted projects are hidden but not purged */
    @Column(nullable = false)
    @Builder.Default
    private boolean archived = false;

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
