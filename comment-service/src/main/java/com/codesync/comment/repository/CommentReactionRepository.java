package com.codesync.comment.repository;

import com.codesync.comment.model.CommentReaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link CommentReaction}.
 */
@Repository
public interface CommentReactionRepository extends JpaRepository<CommentReaction, Long> {

    /** All reactions for a comment. */
    List<CommentReaction> findByCommentId(Long commentId);

    /** Find a specific reaction by user + emoji. */
    Optional<CommentReaction> findByCommentIdAndUserIdAndEmoji(
            Long commentId, Long userId, String emoji);

    /** Check if a user already reacted with a specific emoji. */
    boolean existsByCommentIdAndUserIdAndEmoji(Long commentId, Long userId, String emoji);

    /** Remove a specific reaction. */
    void deleteByCommentIdAndUserIdAndEmoji(Long commentId, Long userId, String emoji);

    /** Count reactions grouped by emoji for a comment. */
    @Query("""
        SELECT r.emoji, COUNT(r) FROM CommentReaction r
        WHERE r.comment.id = :commentId
        GROUP BY r.emoji
        """)
    List<Object[]> countByEmoji(@Param("commentId") Long commentId);

    /** All reactions by a user on comments in a project (for removing when user leaves). */
    void deleteByUserId(Long userId);
}
