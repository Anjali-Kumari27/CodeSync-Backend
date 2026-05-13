package com.codesync.execution.dto;

import com.codesync.execution.model.ExecutionLanguage;
import com.codesync.execution.model.ExecutionStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Response DTO for a code execution result.
 */
@Data
@Builder
public class ExecutionResponse {
    private Long id;
    private Long projectId;
    private Long fileId;
    private ExecutionLanguage language;
    private ExecutionStatus status;
    private String stdout;
    private String stderr;
    private Integer exitCode;
    private Long executionTimeMs;
    private String errorMessage;
    private Long requestedBy;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;

    /** Convenience flag — true if exitCode == 0 and status == COMPLETED */
    private boolean success;
}
