package com.codesync.version.repository;

import com.codesync.version.model.Commit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Spring Data JPA repository for {@link Commit}.
 */
@Repository
public interface CommitRepository extends JpaRepository<Commit, Long> {

    /** All commits on a branch (newest first), paginated. */
    Page<Commit> findByBranchIdOrderByCreatedAtDesc(Long branchId, Pageable pageable);

    /** All commits on a project across all branches (newest first), paginated. */
    Page<Commit> findByProjectIdOrderByCreatedAtDesc(Long projectId, Pageable pageable);

    /** Find commit by its hash. */
    Optional<Commit> findByCommitHash(String commitHash);

    /** Find tagged commits in a project. */
    @Query("SELECT c FROM Commit c WHERE c.projectId = :projectId AND c.tag IS NOT NULL ORDER BY c.createdAt DESC")
    Page<Commit> findTaggedCommits(@Param("projectId") Long projectId, Pageable pageable);

    /** Count commits on a branch. */
    long countByBranchId(Long branchId);

    /** Count total commits in a project. */
    long countByProjectId(Long projectId);

    /** Find commits by author on a specific branch. */
    Page<Commit> findByBranchIdAndAuthorIdOrderByCreatedAtDesc(
            Long branchId, Long authorId, Pageable pageable);
}
