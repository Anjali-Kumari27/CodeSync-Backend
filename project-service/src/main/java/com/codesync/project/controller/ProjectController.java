package com.codesync.project.controller;

import com.codesync.project.dto.*;
import com.codesync.project.model.MemberRole;
import com.codesync.project.service.ProjectService;
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
 * REST Controller for the Project Service.
 *
 * Base path : /api/projects
 * Auth      : X-User-Id header injected by the API Gateway JWT filter.
 *
 * Endpoints:
 *   POST   /api/projects                          – create project
 *   GET    /api/projects/{id}                     – get project by id
 *   GET    /api/projects/my                       – list accessible projects
 *   GET    /api/projects/search?q=...             – keyword search
 *   PATCH  /api/projects/{id}                     – update project
 *   PATCH  /api/projects/{id}/archive             – archive project
 *   DELETE /api/projects/{id}                     – delete project
 *
 *   GET    /api/projects/{id}/members             – list members
 *   POST   /api/projects/{id}/members             – add member
 *   PATCH  /api/projects/{id}/members/{memberId}  – update member role
 *   DELETE /api/projects/{id}/members/{userId}    – remove member
 */
@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
@Tag(name = "Projects", description = "Project management – create, update, delete, and manage collaborators")
public class ProjectController {

    private final ProjectService projectService;

    // ═══════════════════════════════════════════════════════════════════════
    // ── Project Endpoints ───────────────────────────────────────────────────
    // ═══════════════════════════════════════════════════════════════════════

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a new project",
               description = "Creates a project owned by the authenticated user")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Project created successfully"),
        @ApiResponse(responseCode = "400", description = "Validation error"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<ProjectResponse> createProject(
            @Valid @RequestBody CreateProjectRequest request,
            @RequestHeader("X-User-Id") Long userId) {

        ProjectResponse response = projectService.createProject(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get project by ID",
               description = "Returns project details. PRIVATE projects require membership.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Project found"),
        @ApiResponse(responseCode = "403", description = "Access denied"),
        @ApiResponse(responseCode = "404", description = "Project not found")
    })
    public ResponseEntity<ProjectResponse> getProject(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long userId) {

        return ResponseEntity.ok(projectService.getProjectById(id, userId));
    }

    @GetMapping("/my")
    @Operation(summary = "List my accessible projects",
               description = "Returns paginated projects owned by or shared with the authenticated user")
    public ResponseEntity<Page<ProjectResponse>> getMyProjects(
            @RequestHeader("X-User-Id") Long userId,
            @Parameter(description = "Page number (0-indexed)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Items per page")           @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "Sort field")               @RequestParam(defaultValue = "createdAt") String sortBy,
            @Parameter(description = "Sort direction")           @RequestParam(defaultValue = "desc") String direction) {

        Sort sort = direction.equalsIgnoreCase("asc")
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        return ResponseEntity.ok(projectService.getMyProjects(userId, pageable));
    }

    @GetMapping("/search")
    @Operation(summary = "Search projects by name keyword",
               description = "Case-insensitive search across accessible and public projects")
    public ResponseEntity<Page<ProjectResponse>> searchProjects(
            @RequestParam String q,
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "10") int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(projectService.searchProjects(q, userId, pageable));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Update project metadata",
               description = "Partially update name, description, language, or visibility. Owner/ADMIN only.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Project updated"),
        @ApiResponse(responseCode = "403", description = "Access denied"),
        @ApiResponse(responseCode = "404", description = "Project not found")
    })
    public ResponseEntity<ProjectResponse> updateProject(
            @PathVariable Long id,
            @Valid @RequestBody UpdateProjectRequest request,
            @RequestHeader("X-User-Id") Long userId) {

        return ResponseEntity.ok(projectService.updateProject(id, request, userId));
    }

    @PatchMapping("/{id}/archive")
    @Operation(summary = "Archive a project",
               description = "Soft-deletes the project. Only the owner can archive.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Project archived"),
        @ApiResponse(responseCode = "403", description = "Only owner can archive")
    })
    public ResponseEntity<Void> archiveProject(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long userId) {

        projectService.archiveProject(id, userId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a project permanently",
               description = "Permanently removes the project and all its members. Owner only.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Project deleted"),
        @ApiResponse(responseCode = "403", description = "Only owner can delete"),
        @ApiResponse(responseCode = "404", description = "Project not found")
    })
    public ResponseEntity<Void> deleteProject(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long userId) {

        projectService.deleteProject(id, userId);
        return ResponseEntity.noContent().build();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ── Member Endpoints ────────────────────────────────────────────────────
    // ═══════════════════════════════════════════════════════════════════════

    @GetMapping("/{id}/members")
    @Operation(summary = "List all project members",
               description = "Returns all collaborators. Project member or owner only.")
    public ResponseEntity<List<MemberResponse>> listMembers(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long userId) {

        return ResponseEntity.ok(projectService.listMembers(id, userId));
    }

    @PostMapping("/{id}/members")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a member to a project",
               description = "Adds a collaborator with a specified role. Owner/ADMIN only.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Member added"),
        @ApiResponse(responseCode = "409", description = "User is already a member")
    })
    public ResponseEntity<MemberResponse> addMember(
            @PathVariable Long id,
            @Valid @RequestBody AddMemberRequest request,
            @RequestHeader("X-User-Id") Long userId) {

        MemberResponse response = projectService.addMember(id, request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{id}/invite")
    @Operation(summary = "Invite collaborator by email")
    public ResponseEntity<String> inviteMember(
            @PathVariable Long id,
            @Valid @RequestBody InviteMemberRequest request,
            @RequestHeader("X-User-Id") Long userId) {

        projectService.inviteMember(id, request, userId);

        return ResponseEntity.ok("Invitation sent successfully");
    }
    
    @PatchMapping("/{id}/members/{memberId}")
    @Operation(summary = "Update a member's role",
               description = "Change VIEWER / EDITOR / ADMIN role. Owner/ADMIN only.")
    public ResponseEntity<MemberResponse> updateMemberRole(
            @PathVariable Long id,
            @PathVariable Long memberId,
            @RequestParam MemberRole role,
            @RequestHeader("X-User-Id") Long userId) {

        return ResponseEntity.ok(projectService.updateMemberRole(id, memberId, role, userId));
    }

    @DeleteMapping("/{id}/members/{targetUserId}")
    @Operation(summary = "Remove a member from a project",
               description = "Owner/ADMIN can remove anyone. A member can remove themselves (leave).")
    @ApiResponse(responseCode = "204", description = "Member removed")
    public ResponseEntity<Void> removeMember(
            @PathVariable Long id,
            @PathVariable Long targetUserId,
            @RequestHeader("X-User-Id") Long userId) {

        projectService.removeMember(id, targetUserId, userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/invite/{token}/accept")
    @Operation(summary = "Accept invitation")
    public ResponseEntity<String> acceptInvitation(
            @PathVariable String token,
            @RequestHeader("X-User-Id") Long userId) {

        return ResponseEntity.ok(
                projectService.acceptInvitation(token, userId)
        );
    }

    @PostMapping("/invite/{token}/reject")
    @Operation(summary = "Reject invitation")
    public ResponseEntity<String> rejectInvitation(
            @PathVariable String token) {

        return ResponseEntity.ok(
                projectService.rejectInvitation(token)
        );
    }
    
    
    // ═══════════════════════════════════════════════════════════════════════
    // ── Phase 2 Features ────────────────────────────────────────────────────
    // ═══════════════════════════════════════════════════════════════════════

    @PostMapping("/{id}/fork")
    @Operation(summary = "Fork a project")
    public ResponseEntity<ProjectResponse> forkProject(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long userId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(projectService.forkProject(id, userId));
    }

    @PostMapping("/{id}/star")
    @Operation(summary = "Star a project")
    public ResponseEntity<Void> starProject(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long userId) {
        projectService.starProject(id, userId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/star")
    @Operation(summary = "Unstar a project")
    public ResponseEntity<Void> unstarProject(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long userId) {
        projectService.unstarProject(id, userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/analytics")
    @Operation(summary = "Get project analytics")
    public ResponseEntity<ProjectAnalyticsResponse> getAnalytics(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long userId) {
        return ResponseEntity.ok(projectService.getAnalytics(id, userId));
    }

    @GetMapping("/public")
    @Operation(summary = "Browse public projects")
    public ResponseEntity<Page<ProjectResponse>> getPublicProjects(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by("createdAt").descending()
        );

        return ResponseEntity.ok(projectService.getPublicProjects(pageable));
    }

    @GetMapping("/public/search")
    @Operation(summary = "Search public projects")
    public ResponseEntity<Page<ProjectResponse>> searchPublicProjects(
            @RequestParam String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by("createdAt").descending()
        );

        return ResponseEntity.ok(
                projectService.searchPublicProjects(q, pageable)
        );
    }
}
