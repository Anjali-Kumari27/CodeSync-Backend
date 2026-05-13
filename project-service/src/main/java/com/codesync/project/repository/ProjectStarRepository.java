package com.codesync.project.repository;

import com.codesync.project.model.ProjectStar;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProjectStarRepository extends JpaRepository<ProjectStar, Long> {
    boolean existsByProjectIdAndUserId(Long projectId, Long userId);
    Optional<ProjectStar> findByProjectIdAndUserId(Long projectId, Long userId);
    long countByProjectId(Long projectId);
}
