package com.codesync.file.controller;

import com.codesync.file.dto.*;
import com.codesync.file.service.FileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST Controller for the File Service.
 *
 * Base path : /api/files
 * Auth      : X-User-Id header injected by API Gateway JWT filter.
 *
 * Endpoints:
 *  POST   /api/files                                   – create file or directory
 *  GET    /api/files/{id}                              – get file metadata
 *  GET    /api/files/{id}/content                      – get file with content
 *  GET    /api/files/path?projectId=&filePath=         – get by project + path
 *  GET    /api/files/tree/{projectId}                  – full project tree
 *  GET    /api/files/directory/{projectId}/{dirId}     – list directory contents
 *  GET    /api/files/search?projectId=&q=              – keyword search
 *  PATCH  /api/files/{id}                              – update file
 *  DELETE /api/files/{id}                              – soft-delete file
 *  DELETE /api/files/directory/{id}                    – soft-delete directory tree
 *  DELETE /api/files/project/{projectId}               – hard-delete all for project
 */
@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
@Tag(name = "Files", description = "Source file and directory tree management within projects")
public class FileController {

    private final FileService fileService;

    // ═══════════════════════════════════════════════════════════════════════
    // ── CREATE ──────────────────────────────────────────────────────────────
    // ═══════════════════════════════════════════════════════════════════════

    @PostMapping
    @Operation(summary = "Create a file or directory",
               description = "Creates a new source file or directory node within a project")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "File/directory created"),
        @ApiResponse(responseCode = "400", description = "Validation error or invalid operation"),
        @ApiResponse(responseCode = "409", description = "File path already exists in project")
    })
    public ResponseEntity<FileResponse> createFile(
            @Valid @RequestBody CreateFileRequest request,
            @RequestHeader("X-User-Id") Long userId) {

        FileResponse response = fileService.createFile(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ── READ ────────────────────────────────────────────────────────────────
    // ═══════════════════════════════════════════════════════════════════════

    @GetMapping("/{id}")
    @Operation(summary = "Get file metadata by ID",
               description = "Returns file/directory info without content (use /content for content)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "File found"),
        @ApiResponse(responseCode = "404", description = "File not found"),
        @ApiResponse(responseCode = "410", description = "File has been deleted")
    })
    public ResponseEntity<FileResponse> getFile(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long userId) {

        return ResponseEntity.ok(fileService.getFileById(id));
    }

    @GetMapping("/{id}/content")
    @Operation(summary = "Get file content by ID",
               description = "Returns the full file including its source content")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "File with content returned"),
        @ApiResponse(responseCode = "400", description = "Cannot get content of a directory"),
        @ApiResponse(responseCode = "404", description = "File not found")
    })
    public ResponseEntity<FileResponse> getFileContent(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long userId) {

        return ResponseEntity.ok(fileService.getFileWithContent(id));
    }

    @PutMapping("/{id}/content")
    @Operation(summary = "Update file content")
    public ResponseEntity<FileResponse> updateFileContent(
            @PathVariable Long id,
            @RequestBody String content,
            @RequestHeader("X-User-Id") Long userId) {

        UpdateFileRequest request = new UpdateFileRequest();
        request.setContent(content);

        return ResponseEntity.ok(
                fileService.updateFile(id, request, userId)
        );
    }

    @GetMapping("/path")
    @Operation(summary = "Get file by project ID and path",
               description = "Fetch a file using its project and relative path, e.g. 'src/Main.java'")
    public ResponseEntity<FileResponse> getFileByPath(
            @RequestParam Long projectId,
            @RequestParam String filePath,
            @RequestHeader("X-User-Id") Long userId) {

        return ResponseEntity.ok(fileService.getFileByPath(projectId, filePath));
    }

    @GetMapping("/tree/{projectId}")
    @Operation(summary = "Get full project file tree",
               description = "Returns a nested directory/file structure from the project root")
    public ResponseEntity<List<FileResponse>> getProjectTree(
            @PathVariable Long projectId,
            @RequestHeader("X-User-Id") Long userId) {

        return ResponseEntity.ok(fileService.getProjectTree(projectId));
    }

    @GetMapping("/directory/{projectId}/{dirId}")
    @Operation(summary = "List directory contents",
               description = "Returns direct children of the specified directory node")
    public ResponseEntity<List<FileResponse>> getDirectoryContents(
            @PathVariable Long projectId,
            @PathVariable Long dirId,
            @RequestHeader("X-User-Id") Long userId) {

        return ResponseEntity.ok(fileService.getDirectoryContents(projectId, dirId));
    }

    @GetMapping("/search")
    @Operation(summary = "Search files by keyword",
               description = "Case-insensitive search across file names and content within a project")
    public ResponseEntity<List<FileResponse>> searchFiles(
            @RequestParam Long projectId,
            @Parameter(description = "Keyword to search in file names and content")
            @RequestParam String q,
            @RequestHeader("X-User-Id") Long userId) {

        return ResponseEntity.ok(fileService.searchFiles(projectId, q));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ── UPDATE ──────────────────────────────────────────────────────────────
    // ═══════════════════════════════════════════════════════════════════════

    @PatchMapping("/{id}")
    @Operation(summary = "Update a file",
               description = "Partially update content, rename, move to new parent, or change MIME type")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "File updated"),
        @ApiResponse(responseCode = "400", description = "Invalid operation"),
        @ApiResponse(responseCode = "404", description = "File not found"),
        @ApiResponse(responseCode = "409", description = "New path already exists")
    })
    public ResponseEntity<FileResponse> updateFile(
            @PathVariable Long id,
            @Valid @RequestBody UpdateFileRequest request,
            @RequestHeader("X-User-Id") Long userId) {

        return ResponseEntity.ok(fileService.updateFile(id, request, userId));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ── DELETE ──────────────────────────────────────────────────────────────
    // ═══════════════════════════════════════════════════════════════════════

    @DeleteMapping("/{id}")
    @Operation(summary = "Soft-delete a file",
               description = "Marks a file as deleted. Use /directory/{id} for directories.")
    @ApiResponse(responseCode = "204", description = "File deleted")
    public ResponseEntity<Void> deleteFile(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long userId) {

        fileService.deleteFile(id, userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/restore")
    @Operation(summary = "Restore a soft-deleted file",
               description = "Restores a file and its path if the parent directories still exist.")
    @ApiResponse(responseCode = "200", description = "File restored")
    public ResponseEntity<FileResponse> restoreFile(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long userId) {

        return ResponseEntity.ok(fileService.restoreFile(id, userId));
    }

    @DeleteMapping("/directory/{id}")
    @Operation(summary = "Soft-delete a directory and its entire tree",
               description = "Recursively marks the directory and all descendants as deleted")
    @ApiResponse(responseCode = "200", description = "Directory and contents deleted")
    public ResponseEntity<BulkOperationResponse> deleteDirectory(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long userId) {

        return ResponseEntity.ok(fileService.deleteDirectory(id, userId));
    }

    @DeleteMapping("/project/{projectId}")
    @Operation(summary = "Hard-delete all files for a project",
               description = "Permanently removes all files and directories (called when project is deleted)")
    @ApiResponse(responseCode = "204", description = "All project files deleted")
    public ResponseEntity<Void> deleteAllForProject(
            @PathVariable Long projectId,
            @RequestHeader("X-User-Id") Long userId) {

        fileService.deleteAllForProject(projectId);
        return ResponseEntity.noContent().build();
    }
}
