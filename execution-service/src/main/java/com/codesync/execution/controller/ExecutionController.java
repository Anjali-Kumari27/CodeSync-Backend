package com.codesync.execution.controller;

import com.codesync.execution.dto.*;
import com.codesync.execution.service.ExecutionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST Controller for the Execution Service.
 *
 * Base path: /api/executions
 * Auth      : X-User-Id header from API Gateway JWT filter.
 *
 * Endpoints:
 *   POST  /api/executions                              – submit code for execution
 *   GET   /api/executions/{id}                         – get execution result
 *   GET   /api/executions/project/{projectId}          – project execution history
 *   GET   /api/executions/file/{fileId}                – file execution history
 *   GET   /api/executions/project/{projectId}/user     – user's executions in project
 *   GET   /api/executions/project/{projectId}/stats    – execution statistics
 */
@RestController
@RequestMapping("/api/executions")
@RequiredArgsConstructor
@Tag(name = "Code Execution", description = "Sandboxed multi-language code execution and history")
public class ExecutionController {

    private final ExecutionService executionService;

    @PostMapping
    @Operation(summary = "Submit code for execution",
               description = "Runs code in a sandboxed subprocess. Supported languages: JAVA, PYTHON, JAVASCRIPT, TYPESCRIPT, C, CPP, GO, RUST, KOTLIN, BASH.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Execution completed (check status/exitCode for success)"),
        @ApiResponse(responseCode = "400", description = "Validation error"),
        @ApiResponse(responseCode = "408", description = "Execution timed out"),
        @ApiResponse(responseCode = "503", description = "Sandbox execution error")
    })
    public ResponseEntity<ExecutionResponse> execute(
            @Valid @RequestBody ExecutionRequest request,
            @RequestHeader("X-User-Id") Long userId) {

        ExecutionResponse response = executionService.execute(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get an execution record by ID",
               description = "Returns full result including stdout, stderr, exit code, and timing.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Execution found"),
        @ApiResponse(responseCode = "404", description = "Execution not found")
    })
    public ResponseEntity<ExecutionResponse> getExecution(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long userId) {

        return ResponseEntity.ok(executionService.getExecution(id));
    }

    @GetMapping("/project/{projectId}")
    @Operation(summary = "Get paginated execution history for a project (newest first)")
    public ResponseEntity<Page<ExecutionResponse>> getProjectHistory(
            @PathVariable Long projectId,
            @Parameter(description = "Page number (0-indexed)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Items per page")          @RequestParam(defaultValue = "20") int size,
            @RequestHeader("X-User-Id") Long userId) {

        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(executionService.getProjectHistory(projectId, pageable));
    }

    @GetMapping("/file/{fileId}")
    @Operation(summary = "Get paginated execution history for a specific file")
    public ResponseEntity<Page<ExecutionResponse>> getFileHistory(
            @PathVariable Long fileId,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestHeader("X-User-Id") Long userId) {

        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(executionService.getFileHistory(fileId, pageable));
    }

    @GetMapping("/project/{projectId}/user")
    @Operation(summary = "Get the calling user's executions in a project")
    public ResponseEntity<Page<ExecutionResponse>> getUserHistory(
            @PathVariable Long projectId,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestHeader("X-User-Id") Long userId) {

        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(executionService.getUserHistory(projectId, userId, pageable));
    }

    @GetMapping("/project/{projectId}/stats")
    @Operation(summary = "Get execution statistics for a project",
               description = "Returns total count by status, average execution time, and language breakdown.")
    public ResponseEntity<ExecutionStatsResponse> getStats(
            @PathVariable Long projectId,
            @RequestHeader("X-User-Id") Long userId) {

        return ResponseEntity.ok(executionService.getStats(projectId));
    }

    @PatchMapping("/{id}/cancel")
    @Operation(summary = "Cancel a queued execution")
    public ResponseEntity<ExecutionResponse> cancelExecution(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long userId) {

        return ResponseEntity.ok(executionService.cancelExecution(id, userId));
    }
}
