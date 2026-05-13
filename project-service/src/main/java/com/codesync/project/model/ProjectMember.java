package com.codesync.project.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * ProjectMember entity – represents a user's membership in a project.
 *
 * The project owner is recorded in Project.ownerId.
 * Every other collaborator is represented as a ProjectMember row.
 *
 * Roles:
 *   GUEST  – read-only access to files and comments
 *   DEVELOPER  – can edit files and add comments
 *   ADMIN   – can manage members (add/remove), but cannot delete the project
 */
@Entity
@Table(
    name = "project_members",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_project_member",
            columnNames = {"project_id", "user_id"}
        )
    },
    indexes = {
        @Index(name = "idx_member_user",    columnList = "user_id"),
        @Index(name = "idx_member_project", columnList = "project_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProjectMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    /** References a user in auth-service (no FK across services) */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private MemberRole role = MemberRole.GUEST;

    @Column(name = "joined_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime joinedAt = LocalDateTime.now();
}
