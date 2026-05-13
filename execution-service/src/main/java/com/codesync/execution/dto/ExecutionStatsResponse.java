package com.codesync.execution.dto;

import com.codesync.execution.model.ExecutionLanguage;
import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * Aggregated statistics for a project's execution history.
 */
@Data
@Builder
public class ExecutionStatsResponse {
    private Long projectId;
    private long totalExecutions;
    private long completedCount;
    private long failedCount;
    private long queuedCount;
    private Double avgExecutionTimeMs;
    /** Language → count map, e.g. {PYTHON: 12, JAVA: 4} */
    private Map<ExecutionLanguage, Long> languageBreakdown;
}
