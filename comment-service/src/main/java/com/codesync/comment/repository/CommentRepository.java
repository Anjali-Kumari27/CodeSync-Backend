package com.codesync.comment.repository;

import com.codesync.comment.model.Comment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository for {@link Comment}.
 */
@Repository
public interface CommentRepository extends JpaRepository<Comment, Long> {

    /** Root-level (non-reply) comments for a file, newest first (paginated). */
    Page<Comment> findByFileIdAndParentIsNullAndDeletedFalseOrderByCreatedAtDesc(
            Long fileId, Pageable pageable);

    /** Root comments anchored to a specific line, sorted oldest first (thread order). */
    List<Comment> findByFileIdAndLineNumberAndParentIsNullAndDeletedFalseOrderByCreatedAtAsc(
            Long fileId, Integer lineNumber);

    /** Direct replies to a root comment, sorted oldest first. */
    List<Comment> findByParentIdAndDeletedFalseOrderByCreatedAtAsc(Long parentId);

    /** All root comments for a project (for project-level review dashboard). */
    Page<Comment> findByProjectIdAndParentIsNullAndDeletedFalseOrderByCreatedAtDesc(
            Long projectId, Pageable pageable);

    /** Unresolved root comments for a file. */
    List<Comment> findByFileIdAndParentIsNullAndResolvedFalseAndDeletedFalse(Long fileId);

    /** Count unresolved comments on a file. */
    long countByFileIdAndResolvedFalseAndDeletedFalseAndParentIsNull(Long fileId);

    /** All comments authored by a user in a project. */
    Page<Comment> findByProjectIdAndAuthorIdAndDeletedFalseOrderByCreatedAtDesc(
            Long projectId, Long authorId, Pageable pageable);

    /** Search comments by keyword in body text. */
    @Query("""
        SELECT c FROM Comment c
        WHERE c.projectId = :projectId
          AND c.deleted = false
          AND LOWER(c.body) LIKE LOWER(CONCAT('%', :keyword, '%'))
        ORDER BY c.createdAt DESC
        """)
    Page<Comment> searchByKeyword(
            @Param("projectId") Long projectId,
            @Param("keyword")   String keyword,
            Pageable pageable);
}
