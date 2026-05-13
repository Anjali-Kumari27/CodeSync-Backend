package com.codesync.comment.controller;

import com.codesync.comment.dto.*;
import com.codesync.comment.service.CommentService;
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
import java.util.Map;

/**
 * REST Controller for the Comment Service.
 *
 * Base path : /api/comments
 * Auth      : X-User-Id header from API Gateway JWT filter.
 *
 * Comment CRUD:
 *   POST   /api/comments                                 – post comment/reply
 *   GET    /api/comments/{id}                            – get comment (with replies)
 *   PATCH  /api/comments/{id}                            – edit body (author only)
 *   DELETE /api/comments/{id}                            – soft-delete (author only)
 *
 * Thread management:
 *   PATCH  /api/comments/{id}/resolve                   – resolve thread
 *   PATCH  /api/comments/{id}/reopen                    – reopen thread
 *
 * Query views:
 *   GET    /api/comments/file/{fileId}                  – paginated file comments
 *   GET    /api/comments/file/{fileId}/line/{line}      – inline line comments
 *   GET    /api/comments/file/{fileId}/unresolved       – unresolved threads
 *   GET    /api/comments/project/{projectId}            – project comment feed
 *   GET    /api/comments/search?projectId=&q=           – keyword search
 *
 * Reactions:
 *   POST   /api/comments/{id}/reactions                 – add reaction
 *   DELETE /api/comments/{id}/reactions/{emoji}         – remove reaction
 *   GET    /api/comments/{id}/reactions                 – get reaction counts
 */
@RestController
@RequestMapping("/api/comments")
@RequiredArgsConstructor
@Tag(name = "Comments", description = "Inline code comments, review threads, and emoji reactions")
public class CommentController {

    private final CommentService commentService;

    // ═══════════════════════════════════════════════════════════════════════
    // ── COMMENT CRUD ────────────────────────────────────────────────────────
    // ═══════════════════════════════════════════════════════════════════════

    @PostMapping
    @Operation(summary = "Post a comment or reply",
               description = "Create an inline or file-level comment. Set parentId to reply to a thread.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Comment created"),
        @ApiResponse(responseCode = "400", description = "Validation or invalid threading")
    })
    public ResponseEntity<CommentResponse> createComment(
            @Valid @RequestBody CreateCommentRequest request,
            @RequestHeader("X-User-Id") Long userId) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(commentService.createComment(request, userId));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a comment by ID (includes replies and reactions)")
    @ApiResponse(responseCode = "200", description = "Comment found")
    public ResponseEntity<CommentResponse> getComment(@PathVariable Long id) {
        return ResponseEntity.ok(commentService.getComment(id));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Edit a comment body (author only)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Comment updated"),
        @ApiResponse(responseCode = "403", description = "Not the author")
    })
    public ResponseEntity<CommentResponse> updateComment(
            @PathVariable Long id,
            @Valid @RequestBody UpdateCommentRequest request,
            @RequestHeader("X-User-Id") Long userId) {

        return ResponseEntity.ok(commentService.updateComment(id, request, userId));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Soft-delete a comment (author only)",
               description = "Comment body is replaced with [deleted]. Replies remain visible.")
    @ApiResponse(responseCode = "204", description = "Comment deleted")
    public ResponseEntity<Void> deleteComment(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long userId) {

        commentService.deleteComment(id, userId);
        return ResponseEntity.noContent().build();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ── THREAD MANAGEMENT ───────────────────────────────────────────────────
    // ═══════════════════════════════════════════════════════════════════════

    @PatchMapping("/{id}/resolve")
    @Operation(summary = "Resolve a comment thread",
               description = "Mark a root comment as resolved. Any project member can resolve.")
    @ApiResponse(responseCode = "200", description = "Thread resolved")
    public ResponseEntity<CommentResponse> resolveComment(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long userId) {

        return ResponseEntity.ok(commentService.resolveComment(id, userId));
    }

    @PatchMapping("/{id}/reopen")
    @Operation(summary = "Reopen a resolved thread")
    @ApiResponse(responseCode = "200", description = "Thread reopened")
    public ResponseEntity<CommentResponse> reopenComment(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long userId) {

        return ResponseEntity.ok(commentService.reopenComment(id, userId));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ── QUERY VIEWS ─────────────────────────────────────────────────────────
    // ═══════════════════════════════════════════════════════════════════════

    @GetMapping("/file/{fileId}")
    @Operation(summary = "Get paginated comments for a file (newest first)")
    public ResponseEntity<Page<CommentResponse>> getFileComments(
            @PathVariable Long fileId,
            @Parameter(description = "Page number (0-indexed)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Items per page")          @RequestParam(defaultValue = "20") int size,
            @RequestHeader("X-User-Id") Long userId) {

        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(commentService.getFileComments(fileId, pageable));
    }

    @GetMapping("/file/{fileId}/line/{lineNumber}")
    @Operation(summary = "Get all comment threads anchored to a specific line",
               description = "Returns threads in chronological order for the given line number")
    public ResponseEntity<List<CommentResponse>> getLineComments(
            @PathVariable Long fileId,
            @PathVariable int lineNumber,
            @RequestHeader("X-User-Id") Long userId) {

        return ResponseEntity.ok(commentService.getLineComments(fileId, lineNumber));
    }

    @GetMapping("/file/{fileId}/unresolved")
    @Operation(summary = "Get all unresolved comment threads for a file")
    public ResponseEntity<List<CommentResponse>> getUnresolvedComments(
            @PathVariable Long fileId,
            @RequestHeader("X-User-Id") Long userId) {

        return ResponseEntity.ok(commentService.getUnresolvedComments(fileId));
    }

    @GetMapping("/project/{projectId}")
    @Operation(summary = "Get paginated project comment feed (all files, newest first)")
    public ResponseEntity<Page<CommentResponse>> getProjectComments(
            @PathVariable Long projectId,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestHeader("X-User-Id") Long userId) {

        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(commentService.getProjectComments(projectId, pageable));
    }

    @GetMapping("/search")
    @Operation(summary = "Search comments by keyword within a project")
    public ResponseEntity<Page<CommentResponse>> searchComments(
            @RequestParam Long projectId,
            @RequestParam String q,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestHeader("X-User-Id") Long userId) {

        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(commentService.searchComments(projectId, q, pageable));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ── REACTIONS ───────────────────────────────────────────────────────────
    // ═══════════════════════════════════════════════════════════════════════

    @PostMapping("/{id}/reactions")
    @Operation(summary = "Add an emoji reaction to a comment",
               description = "Each user can add the same emoji only once per comment")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Reaction added, returns updated counts"),
        @ApiResponse(responseCode = "409", description = "Already reacted with this emoji")
    })
    public ResponseEntity<Map<String, Long>> addReaction(
            @PathVariable Long id,
            @RequestParam String emoji,
            @RequestHeader("X-User-Id") Long userId) {

        return ResponseEntity.ok(commentService.addReaction(id, emoji, userId));
    }

    @DeleteMapping("/{id}/reactions/{emoji}")
    @Operation(summary = "Remove your emoji reaction from a comment")
    @ApiResponse(responseCode = "200", description = "Reaction removed, returns updated counts")
    public ResponseEntity<Map<String, Long>> removeReaction(
            @PathVariable Long id,
            @PathVariable String emoji,
            @RequestHeader("X-User-Id") Long userId) {

        return ResponseEntity.ok(commentService.removeReaction(id, emoji, userId));
    }

    @GetMapping("/{id}/reactions")
    @Operation(summary = "Get emoji reaction counts for a comment")
    public ResponseEntity<Map<String, Long>> getReactions(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long userId) {

        return ResponseEntity.ok(commentService.getReactionCounts(id));
    }
}
