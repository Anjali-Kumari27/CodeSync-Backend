package com.codesync.version.controller;

import com.codesync.version.dto.*;
import com.codesync.version.service.VersionService;
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

import java.util.List;

/**
 * REST Controller for the Version Service.
 *
 * Base path : /api/versions
 * Auth      : X-User-Id header from API Gateway JWT filter.
 *
 * Branch endpoints:
 *   POST   /api/versions/branches                        – create branch
 *   GET    /api/versions/branches/{id}                   – get branch
 *   GET    /api/versions/branches/project/{projectId}    – list project branches
 *   DELETE /api/versions/branches/{id}                   – delete branch
 *
 * Commit endpoints:
 *   POST   /api/versions/commits                             – create commit
 *   GET    /api/versions/commits/{id}                        – get commit (with files)
 *   GET    /api/versions/commits/hash/{hash}                 – get commit by hash
 *   GET    /api/versions/commits/branch/{branchId}           – branch commit history
 *   GET    /api/versions/commits/project/{projectId}         – project commit history
 *   GET    /api/versions/commits/project/{projectId}/tags    – tagged releases
 *
 * Diff endpoint:
 *   GET    /api/versions/diff?from={id}&to={id}              – diff two commits
 */
@RestController
@RequestMapping("/api/versions")
@RequiredArgsConstructor
@Tag(name = "Version Control", description = "Git-inspired branch and commit management for CodeSync projects")
public class VersionController {

    private final VersionService versionService;

    // ═══════════════════════════════════════════════════════════════════════
    // ── BRANCHES ────────────────────────────────────────────────────────────
    // ═══════════════════════════════════════════════════════════════════════

    @PostMapping("/branches")
    @Operation(summary = "Create a new branch",
               description = "Creates a branch within a project. First branch is automatically set as default.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Branch created"),
        @ApiResponse(responseCode = "409", description = "Branch name already exists in project")
    })
    public ResponseEntity<BranchResponse> createBranch(
            @Valid @RequestBody CreateBranchRequest request,
            @RequestHeader("X-User-Id") Long userId) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(versionService.createBranch(request, userId));
    }

    @GetMapping("/branches/{id}")
    @Operation(summary = "Get a branch by ID")
    @ApiResponse(responseCode = "200", description = "Branch found")
    public ResponseEntity<BranchResponse> getBranch(@PathVariable Long id) {
        return ResponseEntity.ok(versionService.getBranch(id));
    }

    @GetMapping("/branches/{branchId}/files/{fileId}")
    public ResponseEntity<String> getBranchFileContent(
            @PathVariable Long branchId,
            @PathVariable Long fileId) {

        return ResponseEntity.ok(
                versionService.getLatestFileContent(branchId, fileId)
        );
    }
    
    @GetMapping("/branches/project/{projectId}")
    @Operation(summary = "List all branches for a project")
    public ResponseEntity<List<BranchResponse>> listBranches(@PathVariable Long projectId) {
        return ResponseEntity.ok(versionService.listBranches(projectId));
    }

    @GetMapping("/test")
    public String test() {
        return "VERSION SERVICE WORKING";
    }
    
    @DeleteMapping("/branches/{id}")
    @Operation(summary = "Delete a branch",
               description = "Deletes a non-default branch. Default (main) branch cannot be deleted.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Branch deleted"),
        @ApiResponse(responseCode = "400", description = "Cannot delete default branch"),
        @ApiResponse(responseCode = "404", description = "Branch not found")
    })
    public ResponseEntity<Void> deleteBranch(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long userId) {

        versionService.deleteBranch(id, userId);
        return ResponseEntity.noContent().build();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ── COMMITS ─────────────────────────────────────────────────────────────
    // ═══════════════════════════════════════════════════════════════════════

    @PostMapping("/commits")
    @Operation(summary = "Create a commit",
               description = "Creates an immutable snapshot of files on a branch with a SHA-256 commit hash")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Commit created"),
        @ApiResponse(responseCode = "400", description = "Validation error"),
        @ApiResponse(responseCode = "409", description = "Tag already exists in project")
    })
    public ResponseEntity<CommitResponse> createCommit(
            @Valid @RequestBody CreateCommitRequest request,
            @RequestHeader("X-User-Id") Long userId) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(versionService.createCommit(request, userId));
    }

    @GetMapping("/commits/{id}")
    @Operation(summary = "Get commit by ID (with file snapshots)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Commit found"),
        @ApiResponse(responseCode = "404", description = "Commit not found")
    })
    public ResponseEntity<CommitResponse> getCommit(@PathVariable Long id) {
        return ResponseEntity.ok(versionService.getCommit(id));
    }

    @GetMapping("/commits/hash/{hash}")
    @Operation(summary = "Get commit by hash",
               description = "Find a commit using its full SHA-256 hash")
    public ResponseEntity<CommitResponse> getCommitByHash(@PathVariable String hash) {
        return ResponseEntity.ok(versionService.getCommitByHash(hash));
    }

    @GetMapping("/commits/branch/{branchId}")
    @Operation(summary = "Get commit history for a branch (paginated, newest first)")
    public ResponseEntity<Page<CommitResponse>> getBranchHistory(
            @PathVariable Long branchId,
            @Parameter(description = "Page number (0-indexed)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Items per page")          @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(versionService.getBranchHistory(branchId, pageable));
    }

    @PostMapping("/commits/{id}/restore")
    @Operation(summary = "Restore a commit snapshot",
               description = "Returns the commit files so the client can restore them in the active project workspace")
    public ResponseEntity<CommitResponse> restoreCommit(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long userId) {

        return ResponseEntity.ok(versionService.getCommit(id));
    }

    @GetMapping("/commits/project/{projectId}")
    @Operation(summary = "Get commit history across all branches (paginated)")
    public ResponseEntity<Page<CommitResponse>> getProjectHistory(
            @PathVariable Long projectId,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(versionService.getProjectHistory(projectId, pageable));
    }

    @GetMapping("/commits/project/{projectId}/tags")
    @Operation(summary = "Get all tagged (release) commits for a project")
    public ResponseEntity<Page<CommitResponse>> getTaggedCommits(
            @PathVariable Long projectId,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(versionService.getTaggedCommits(projectId, pageable));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ── DIFF ────────────────────────────────────────────────────────────────
    // ═══════════════════════════════════════════════════════════════════════

    @GetMapping("/diff")
    @Operation(summary = "Diff two commits",
               description = "Returns a list of files that changed between two commits (ADDED / MODIFIED / DELETED)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Diff computed"),
        @ApiResponse(responseCode = "404", description = "One or both commits not found")
    })
    public ResponseEntity<DiffResponse> diffCommits(
            @Parameter(description = "Source commit ID") @RequestParam Long from,
            @Parameter(description = "Target commit ID") @RequestParam Long to,
            @RequestHeader("X-User-Id") Long userId) {

        return ResponseEntity.ok(versionService.diffCommits(from, to));
    }
}
