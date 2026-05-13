package com.codesync.execution.repository;

import com.codesync.execution.model.ExecutionLanguage;
import com.codesync.execution.model.ExecutionRecord;
import com.codesync.execution.model.ExecutionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository for {@link ExecutionRecord}.
 */
@Repository
public interface ExecutionRecordRepository extends JpaRepository<ExecutionRecord, Long> {

    /** Paginated history for a project (newest first). */
    Page<ExecutionRecord> findByProjectIdOrderByCreatedAtDesc(Long projectId, Pageable pageable);

    /** Paginated history for a specific file (newest first). */
    Page<ExecutionRecord> findByFileIdOrderByCreatedAtDesc(Long fileId, Pageable pageable);

    /** All executions by a user in a project. */
    Page<ExecutionRecord> findByProjectIdAndRequestedByOrderByCreatedAtDesc(
            Long projectId, Long requestedBy, Pageable pageable);

    /** All executions for a project filtered by status. */
    List<ExecutionRecord> findByProjectIdAndStatus(Long projectId, ExecutionStatus status);

    /** Count executions by language in a project. */
    @Query("""
        SELECT e.language, COUNT(e) FROM ExecutionRecord e
        WHERE e.projectId = :projectId
        GROUP BY e.language
        ORDER BY COUNT(e) DESC
        """)
    List<Object[]> countByLanguage(@Param("projectId") Long projectId);

    /** Average execution time for a project. */
    @Query("SELECT AVG(e.executionTimeMs) FROM ExecutionRecord e WHERE e.projectId = :projectId AND e.status = 'COMPLETED'")
    Double avgExecutionTimeMs(@Param("projectId") Long projectId);

    /** Count total executions per status for a project. */
    long countByProjectIdAndStatus(Long projectId, ExecutionStatus status);
}
