package com.codesync.comment.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Reaction entity – emoji reactions on a comment (like GitHub's reaction system).
 *
 * Each row represents one user's reaction to one comment.
 * Unique constraint ensures a user can only react once with the same emoji per comment.
 */
@Entity
@Table(
    name = "comment_reactions",
    uniqueConstraints = {
        @UniqueConstraint(
            name  = "uk_reaction_comment_user_emoji",
            columnNames = {"comment_id", "user_id", "emoji"}
        )
    },
    indexes = {
        @Index(name = "idx_reaction_comment", columnList = "comment_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommentReaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "comment_id", nullable = false)
    private Comment comment;

    /** User who reacted */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /**
     * Emoji short code, e.g. "👍", "❤️", "😄", "🎉", "👀", "🚀".
     * Stored as Unicode directly.
     */
    @Column(nullable = false, length = 20)
    private String emoji;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
