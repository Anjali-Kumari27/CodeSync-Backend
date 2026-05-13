package com.codesync.execution.service;

import com.codesync.execution.config.RabbitMQConfig;
import com.codesync.execution.dto.ExecutionMessage;
import com.codesync.execution.exception.ExecutionTimeoutException;
import com.codesync.execution.model.ExecutionRecord;
import com.codesync.execution.model.ExecutionStatus;
import com.codesync.execution.repository.ExecutionRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExecutionConsumer {

    private final ExecutionRecordRepository recordRepository;
    private final SandboxRunner sandboxRunner;
    private final SimpMessagingTemplate messagingTemplate;

    @RabbitListener(queues = RabbitMQConfig.EXECUTION_QUEUE)
    public void consumeExecutionMessage(ExecutionMessage message) {
        log.info("Received execution message for id={}", message.getExecutionId());
        
        ExecutionRecord record = recordRepository.findById(message.getExecutionId()).orElse(null);
        if (record == null) {
            log.warn("ExecutionRecord not found for id={}", message.getExecutionId());
            return;
        }

        // If it was cancelled while in queue, skip it.
        if (record.getStatus() != ExecutionStatus.QUEUED) {
            log.info("Execution id={} is not QUEUED (status={}). Skipping.", record.getId(), record.getStatus());
            return;
        }

        // 2. Mark as RUNNING
        record.setStatus(ExecutionStatus.RUNNING);
        record = recordRepository.save(record);
        
        broadcastStatus(record.getId(), "RUNNING");

        // 3. Run in sandbox
        try {
            // Note: SandboxRunner could be enhanced to stream output chunks to broadcastOutput()
            SandboxRunner.SandboxResult result =
                    sandboxRunner.run(message.getCode(), message.getLanguage(), message.getStdin());

            // 4. Store results
            record.setStdout(result.stdout());
            record.setStderr(result.stderr());
            record.setExitCode(result.exitCode());
            record.setExecutionTimeMs(result.executionTimeMs());
            record.setStatus(ExecutionStatus.COMPLETED);
            record.setCompletedAt(LocalDateTime.now());
            
            broadcastOutput(record.getId(), result.stdout(), false);
            if (result.stderr() != null && !result.stderr().isEmpty()) {
                broadcastOutput(record.getId(), result.stderr(), true);
            }

            log.info("Execution id={} completed in {}ms (exit={})",
                    record.getId(), result.executionTimeMs(), result.exitCode());

        } catch (ExecutionTimeoutException e) {
            record.setStatus(ExecutionStatus.FAILED);
            record.setErrorMessage(e.getMessage());
            record.setCompletedAt(LocalDateTime.now());
            log.warn("Execution id={} timed out: {}", record.getId(), e.getMessage());
            broadcastOutput(record.getId(), e.getMessage(), true);

        } catch (Exception e) {
            record.setStatus(ExecutionStatus.FAILED);
            record.setErrorMessage("Execution error: " + e.getMessage());
            record.setCompletedAt(LocalDateTime.now());
            log.error("Execution id={} failed: {}", record.getId(), e.getMessage(), e);
            broadcastOutput(record.getId(), "Error: " + e.getMessage(), true);

        } finally {
            recordRepository.save(record);
            broadcastStatus(record.getId(), record.getStatus().name());
        }
    }
    
    private void broadcastStatus(Long executionId, String status) {
        messagingTemplate.convertAndSend("/topic/execution/" + executionId + "/status", status);
    }
    
    private void broadcastOutput(Long executionId, String output, boolean isError) {
        if (output == null || output.isEmpty()) return;
        messagingTemplate.convertAndSend("/topic/execution/" + executionId + "/output", 
                isError ? "STDERR: " + output : "STDOUT: " + output);
    }
}
