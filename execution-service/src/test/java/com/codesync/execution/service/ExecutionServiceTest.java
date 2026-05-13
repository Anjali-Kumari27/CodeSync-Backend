package com.codesync.execution.service;

import com.codesync.execution.dto.*;
import com.codesync.execution.exception.*;
import com.codesync.execution.model.*;
import com.codesync.execution.repository.ExecutionRecordRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ExecutionService}.
 * Pure Mockito — no Spring context, no DB, no actual subprocess.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ExecutionService Unit Tests")
class ExecutionServiceTest {

    @Mock ExecutionRecordRepository recordRepository;
    @Mock SandboxRunner             sandboxRunner;
    @Mock org.springframework.amqp.rabbit.core.RabbitTemplate rabbitTemplate;
    @InjectMocks ExecutionService   executionService;

    private static final Long USER_ID    = 1L;
    private static final Long PROJECT_ID = 10L;
    private static final Long RECORD_ID  = 100L;

    private ExecutionRequest sampleRequest() {
        ExecutionRequest req = new ExecutionRequest();
        req.setProjectId(PROJECT_ID);
        req.setCode("print('Hello')");
        req.setLanguage(ExecutionLanguage.PYTHON);
        return req;
    }

    private ExecutionRecord savedRecord(ExecutionStatus status) {
        return ExecutionRecord.builder()
                .id(RECORD_ID).projectId(PROJECT_ID)
                .code("print('Hello')").language(ExecutionLanguage.PYTHON)
                .status(status).requestedBy(USER_ID).build();
    }

    // ═══════════════════════════════════════════════════════════════════════
    @Nested @DisplayName("execute()")
    class Execute {

        @Test @DisplayName("queues execution and publishes to rabbitmq")
        void successfulQueue() {
            // Arrange
            when(recordRepository.save(any(ExecutionRecord.class))).thenAnswer(inv -> {
                ExecutionRecord r = inv.getArgument(0);
                if (r.getId() == null) r = ExecutionRecord.builder()
                        .id(RECORD_ID).projectId(PROJECT_ID).code(r.getCode())
                        .language(r.getLanguage()).status(r.getStatus())
                        .requestedBy(USER_ID).build();
                return r;
            });

            // Act
            ExecutionResponse resp = executionService.execute(sampleRequest(), USER_ID);

            // Assert
            assertThat(resp.getStatus()).isEqualTo(ExecutionStatus.QUEUED);
            verify(rabbitTemplate).convertAndSend(anyString(), anyString(), any(ExecutionMessage.class));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    @Nested @DisplayName("getExecution()")
    class GetExecution {

        @Test @DisplayName("returns execution response for valid ID")
        void returnsExecution() {
            ExecutionRecord record = savedRecord(ExecutionStatus.COMPLETED);
            record.setStdout("Hello");
            record.setExitCode(0);
            record.setExecutionTimeMs(100L);
            when(recordRepository.findById(RECORD_ID)).thenReturn(Optional.of(record));

            ExecutionResponse resp = executionService.getExecution(RECORD_ID);

            assertThat(resp.getId()).isEqualTo(RECORD_ID);
            assertThat(resp.getStatus()).isEqualTo(ExecutionStatus.COMPLETED);
        }

        @Test @DisplayName("throws ResourceNotFoundException for unknown ID")
        void throwsForUnknownId() {
            when(recordRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> executionService.getExecution(999L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("999");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    @Nested @DisplayName("getStats()")
    class GetStats {

        @Test @DisplayName("returns aggregated stats for a project")
        void returnsStats() {
            when(recordRepository.countByProjectIdAndStatus(PROJECT_ID, ExecutionStatus.COMPLETED)).thenReturn(8L);
            when(recordRepository.countByProjectIdAndStatus(PROJECT_ID, ExecutionStatus.FAILED)).thenReturn(2L);
            when(recordRepository.countByProjectIdAndStatus(PROJECT_ID, ExecutionStatus.QUEUED)).thenReturn(0L);
            when(recordRepository.avgExecutionTimeMs(PROJECT_ID)).thenReturn(250.0);
            when(recordRepository.countByLanguage(PROJECT_ID))
                    .thenReturn(List.<Object[]>of(
                            new Object[]{ExecutionLanguage.PYTHON, 6L},
                            new Object[]{ExecutionLanguage.JAVA, 4L}
                    ));

            ExecutionStatsResponse stats = executionService.getStats(PROJECT_ID);

            assertThat(stats.getTotalExecutions()).isEqualTo(10L);
            assertThat(stats.getCompletedCount()).isEqualTo(8L);
            assertThat(stats.getFailedCount()).isEqualTo(2L);
            assertThat(stats.getAvgExecutionTimeMs()).isEqualTo(250.0);
            assertThat(stats.getLanguageBreakdown())
                    .containsEntry(ExecutionLanguage.PYTHON, 6L)
                    .containsEntry(ExecutionLanguage.JAVA, 4L);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    @Nested @DisplayName("getProjectHistory()")
    class GetHistory {

        @Test @DisplayName("returns paginated execution history")
        void returnsPaginatedHistory() {
            ExecutionRecord record = savedRecord(ExecutionStatus.COMPLETED);
            record.setExitCode(0);
            when(recordRepository.findByProjectIdOrderByCreatedAtDesc(eq(PROJECT_ID), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(record)));

            var page = executionService.getProjectHistory(PROJECT_ID, Pageable.unpaged());

            assertThat(page.getTotalElements()).isEqualTo(1);
            assertThat(page.getContent().get(0).getProjectId()).isEqualTo(PROJECT_ID);
        }
    }
}
