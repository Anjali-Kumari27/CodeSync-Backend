package com.codesync.version.repository;

import com.codesync.version.model.Branch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link Branch}.
 */
@Repository
public interface BranchRepository extends JpaRepository<Branch, Long> {

    /** All branches for a project. */
    List<Branch> findByProjectId(Long projectId);

    /** Find a branch by project and name. */
    Optional<Branch> findByProjectIdAndName(Long projectId, String name);

    /** Find the default (main) branch for a project. */
    Optional<Branch> findByProjectIdAndDefaultBranchTrue(Long projectId);

    /** Check if a branch name already exists in a project. */
    boolean existsByProjectIdAndName(Long projectId, String name);

    /** Count branches in a project. */
    long countByProjectId(Long projectId);
}
