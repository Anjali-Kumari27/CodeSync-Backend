package com.codesync.execution.service;

import com.codesync.execution.dto.*;
import com.codesync.execution.exception.ExecutionException;
import com.codesync.execution.exception.ExecutionTimeoutException;
import com.codesync.execution.exception.ResourceNotFoundException;
import com.codesync.execution.model.*;
import com.codesync.execution.repository.ExecutionRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Core orchestration service for code execution.
 *
 * Flow per execution request:
 *  1. Persist an ExecutionRecord with status=QUEUED.
 *  2. Update status to RUNNING.
 *  3. Delegate to SandboxRunner.run() (subprocess-based execution).
 *  4. Store stdout, stderr, exitCode, timing.
 *  5. Update status to COMPLETED or FAILED.
 *  6. Return full ExecutionResponse.
 *
 * Error handling:
 *  - ExecutionTimeoutException  → status=FAILED, errorMessage set
 *  - Any RuntimeException       → status=FAILED, errorMessage set
 *  - The record is always saved even on failure for audit purposes.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ExecutionService {

    private final ExecutionRecordRepository recordRepository;
    private final SandboxRunner             sandboxRunner;

    // ═══════════════════════════════════════════════════════════════════════
    // ── EXECUTE ─────────────────────────────────────────────────────────────
    // ═══════════════════════════════════════════════════════════════════════

    private final org.springframework.amqp.rabbit.core.RabbitTemplate rabbitTemplate;

    /**
     * Submit code for execution asynchronously using RabbitMQ.
     */
    public ExecutionResponse execute(ExecutionRequest req, Long userId) {
        log.info("User {} submitting {} code ({} chars) for project {}",
                userId, req.getLanguage(), req.getCode().length(), req.getProjectId());

        ExecutionRecord record = ExecutionRecord.builder()
                .projectId(req.getProjectId())
                .fileId(req.getFileId())
                .code(req.getCode())
                .language(req.getLanguage())
                .stdin(req.getStdin())
                .status(ExecutionStatus.QUEUED)
                .requestedBy(userId)
                .build();
        record = recordRepository.save(record);

        // Publish to RabbitMQ
        ExecutionMessage message = ExecutionMessage.builder()
                .executionId(record.getId())
                .code(req.getCode())
                .language(req.getLanguage())
                .stdin(req.getStdin())
                .build();
                
        rabbitTemplate.convertAndSend(
                com.codesync.execution.config.RabbitMQConfig.EXECUTION_EXCHANGE,
                com.codesync.execution.config.RabbitMQConfig.EXECUTION_ROUTING_KEY,
                message
        );

        log.info("Execution id={} queued", record.getId());
        return toResponse(record);
    }

    /**
     * Cancel a queued execution. (Running executions are harder to kill via AMQP easily, 
     * but we can mark it failed if it hasn't started or is still in DB as QUEUED).
     */
    public ExecutionResponse cancelExecution(Long id, Long userId) {
        ExecutionRecord record = findOrThrow(id);
        if (record.getStatus() == ExecutionStatus.QUEUED) {
            record.setStatus(ExecutionStatus.FAILED);
            record.setErrorMessage("Cancelled by user");
            record.setCompletedAt(LocalDateTime.now());
            record = recordRepository.save(record);
            log.info("Execution id={} cancelled by user {}", id, userId);
        }
        return toResponse(record);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ── HISTORY ─────────────────────────────────────────────────────────────
    // ═══════════════════════════════════════════════════════════════════════

    /** Get a single execution record by ID. */
    @Transactional(readOnly = true)
    public ExecutionResponse getExecution(Long id) {
        return toResponse(findOrThrow(id));
    }

    /** Paginated execution history for a project. */
    @Transactional(readOnly = true)
    public Page<ExecutionResponse> getProjectHistory(Long projectId, Pageable pageable) {
        return recordRepository
                .findByProjectIdOrderByCreatedAtDesc(projectId, pageable)
                .map(this::toResponse);
    }

    /** Paginated execution history for a specific file. */
    @Transactional(readOnly = true)
    public Page<ExecutionResponse> getFileHistory(Long fileId, Pageable pageable) {
        return recordRepository
                .findByFileIdOrderByCreatedAtDesc(fileId, pageable)
                .map(this::toResponse);
    }

    /** Paginated executions by a specific user in a project. */
    @Transactional(readOnly = true)
    public Page<ExecutionResponse> getUserHistory(Long projectId, Long userId, Pageable pageable) {
        return recordRepository
                .findByProjectIdAndRequestedByOrderByCreatedAtDesc(projectId, userId, pageable)
                .map(this::toResponse);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ── STATS ───────────────────────────────────────────────────────────────
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Aggregate execution statistics for a project.
     */
    @Transactional(readOnly = true)
    public ExecutionStatsResponse getStats(Long projectId) {
        long total      = recordRepository.countByProjectIdAndStatus(projectId, ExecutionStatus.COMPLETED)
                        + recordRepository.countByProjectIdAndStatus(projectId, ExecutionStatus.FAILED)
                        + recordRepository.countByProjectIdAndStatus(projectId, ExecutionStatus.QUEUED);
        long completed  = recordRepository.countByProjectIdAndStatus(projectId, ExecutionStatus.COMPLETED);
        long failed     = recordRepository.countByProjectIdAndStatus(projectId, ExecutionStatus.FAILED);
        long queued     = recordRepository.countByProjectIdAndStatus(projectId, ExecutionStatus.QUEUED);
        Double avgTime  = recordRepository.avgExecutionTimeMs(projectId);

        Map<ExecutionLanguage, Long> langBreakdown = new LinkedHashMap<>();
        recordRepository.countByLanguage(projectId)
                .forEach(row -> langBreakdown.put((ExecutionLanguage) row[0], (Long) row[1]));

        return ExecutionStatsResponse.builder()
                .projectId(projectId)
                .totalExecutions(total)
                .completedCount(completed)
                .failedCount(failed)
                .queuedCount(queued)
                .avgExecutionTimeMs(avgTime)
                .languageBreakdown(langBreakdown)
                .build();
    }

    // ── Private Helpers ────────────────────────────────────────────────────

    private ExecutionRecord findOrThrow(Long id) {
        return recordRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ExecutionRecord", "id", id));
    }

    private ExecutionResponse toResponse(ExecutionRecord r) {
        return ExecutionResponse.builder()
                .id(r.getId())
                .projectId(r.getProjectId())
                .fileId(r.getFileId())
                .language(r.getLanguage())
                .status(r.getStatus())
                .stdout(r.getStdout())
                .stderr(r.getStderr())
                .exitCode(r.getExitCode())
                .executionTimeMs(r.getExecutionTimeMs())
                .errorMessage(r.getErrorMessage())
                .requestedBy(r.getRequestedBy())
                .createdAt(r.getCreatedAt())
                .completedAt(r.getCompletedAt())
                .success(r.getStatus() == ExecutionStatus.COMPLETED
                        && r.getExitCode() != null && r.getExitCode() == 0)
                .build();
    }
}
