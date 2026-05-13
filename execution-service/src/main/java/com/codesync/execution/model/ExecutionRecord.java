package com.codesync.execution.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * ExecutionRecord – persisted audit log of every code execution request.
 *
 * Design:
 *  - Each execution request is recorded BEFORE running (status = QUEUED/RUNNING).
 *  - Status is updated to COMPLETED or FAILED after execution finishes.
 *  - stdout, stderr, and exitCode are stored after completion.
 *  - Execution is always sandboxed (timed out, output-capped).
 *  - Input (stdin) can be optionally supplied by the caller.
 *
 * Security:
 *  - Code is NEVER stored in a file system accessible to other users.
 *  - Execution is performed in an isolated subprocess with a hard timeout.
 *  - This is a SIMULATION for assessment purposes: in production, use
 *    Docker/Firecracker microVMs or a service like Judge0 / Piston API.
 */
@Entity
@Table(
    name = "execution_records",
    indexes = {
        @Index(name = "idx_exec_project",  columnList = "project_id"),
        @Index(name = "idx_exec_file",     columnList = "file_id"),
        @Index(name = "idx_exec_user",     columnList = "requested_by"),
        @Index(name = "idx_exec_status",   columnList = "status"),
        @Index(name = "idx_exec_created",  columnList = "created_at")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExecutionRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Project this execution belongs to */
    @Column(name = "project_id", nullable = false)
    private Long projectId;

    /** Optional: file from the File Service whose content was executed */
    @Column(name = "file_id")
    private Long fileId;

    /** The source code to execute */
    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String code;

    /** Programming language of the code */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ExecutionLanguage language;

    /** Optional stdin to pass to the running program */
    @Column(columnDefinition = "TEXT")
    private String stdin;

    /** Current execution status */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ExecutionStatus status = ExecutionStatus.QUEUED;

    /** Standard output (stdout) captured from the program */
    @Column(columnDefinition = "TEXT")
    private String stdout;

    /** Standard error (stderr) captured from the program */
    @Column(columnDefinition = "TEXT")
    private String stderr;

    /** Process exit code (0 = success) */
    @Column(name = "exit_code")
    private Integer exitCode;

    /** Execution wall-clock time in milliseconds */
    @Column(name = "execution_time_ms")
    private Long executionTimeMs;

    /** Error message if execution itself failed (timeout, sandbox error) */
    @Column(name = "error_message", length = 500)
    private String errorMessage;

    /** User who triggered this execution */
    @Column(name = "requested_by", nullable = false)
    private Long requestedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "completed_at")
    private LocalDateTime completedAt;
}
