package com.codesync.project.repository;

import com.codesync.project.model.MemberRole;
import com.codesync.project.model.ProjectMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link ProjectMember}.
 */
@Repository
public interface ProjectMemberRepository extends JpaRepository<ProjectMember, Long> {

    /** All members of a given project. */
    List<ProjectMember> findByProjectId(Long projectId);

    /** Find a specific member record by project and user. */
    Optional<ProjectMember> findByProjectIdAndUserId(Long projectId, Long userId);

    /** Check if a user is already a member of a project. */
    boolean existsByProjectIdAndUserId(Long projectId, Long userId);

    /** Count how many members have a given role in a project. */
    long countByProjectIdAndRole(Long projectId, MemberRole role);

    /** Remove a member from a project. */
    void deleteByProjectIdAndUserId(Long projectId, Long userId);
}
