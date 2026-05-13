package com.codesync.project.repository;

import com.codesync.project.model.Project;
import com.codesync.project.model.Visibility;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    Page<Project> findByOwnerIdAndArchivedFalse(Long ownerId, Pageable pageable);

    Page<Project> findByVisibilityAndArchivedFalse(Visibility visibility, Pageable pageable);

    @Query("""
        SELECT DISTINCT p FROM Project p
        LEFT JOIN p.members m
        WHERE p.archived = false
          AND (p.ownerId = :userId OR m.userId = :userId)
        """)
    Page<Project> findAccessibleByUser(@Param("userId") Long userId, Pageable pageable);

    @Query("""
        SELECT DISTINCT p FROM Project p
        LEFT JOIN p.members m
        WHERE p.archived = false
          AND LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
          AND (p.ownerId = :userId OR m.userId = :userId OR p.visibility = 'PUBLIC')
        """)
    Page<Project> searchByKeyword(
            @Param("keyword") String keyword,
            @Param("userId") Long userId,
            Pageable pageable
    );

    boolean existsByIdAndOwnerId(Long projectId, Long ownerId);

    long countByForkedFromProjectId(Long projectId);

    
    Page<Project> findByVisibilityAndArchivedFalseOrderByCreatedAtDesc(
            Visibility visibility,
            Pageable pageable
    );

    @Query("""
        SELECT p FROM Project p
        WHERE p.archived = false
          AND p.visibility = 'PUBLIC'
          AND LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
        ORDER BY p.createdAt DESC
        """)
    Page<Project> searchPublicProjects(
            @Param("keyword") String keyword,
            Pageable pageable
    );
}

