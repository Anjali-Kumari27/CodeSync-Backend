package com.codesync.comment.service;

import com.codesync.comment.dto.*;
import com.codesync.comment.exception.*;
import com.codesync.comment.model.Comment;
import com.codesync.comment.model.CommentReaction;
import com.codesync.comment.repository.CommentReactionRepository;
import com.codesync.comment.repository.CommentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Core business logic for the Comment Service.
 *
 * Features:
 *  - File-level and inline (line/column) code comments
 *  - Two-level thread hierarchy: root comments + replies (no deep nesting)
 *  - Edit (author only), soft-delete (author only)
 *  - Resolve / reopen comment threads
 *  - Emoji reactions with duplicate prevention
 *  - Paginated views: per-file, per-project, per-author, keyword search
 *
 * Authorization rules:
 *  - Any project member can POST a comment or reply
 *  - Only the author can EDIT or DELETE their own comment
 *  - Any project member can RESOLVE or REOPEN a thread
 *  - Any project member can ADD or REMOVE their own reaction
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class CommentService {

    private final CommentRepository         commentRepository;
    private final CommentReactionRepository reactionRepository;
    private final com.codesync.comment.repository.CommentHistoryRepository historyRepository;
    private final org.springframework.messaging.simp.SimpMessagingTemplate messagingTemplate;
    
    private void broadcastEvent(Long projectId, String eventType, Object payload) {
        messagingTemplate.convertAndSend("/topic/project/" + projectId + "/comments", 
                Map.of("type", eventType, "payload", payload));
    }
    
    private void extractAndNotifyMentions(Comment comment) {
        if (comment.getBody() == null) return;
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("@(\\w+)").matcher(comment.getBody());
        while (matcher.find()) {
            String username = matcher.group(1);
            messagingTemplate.convertAndSend("/topic/project/" + comment.getProjectId() + "/mentions",
                    Map.of("mention", username, "commentId", comment.getId(), "fileId", comment.getFileId()));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ── CREATE ──────────────────────────────────────────────────────────────
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Post a new comment or reply on a file.
     */
    public CommentResponse createComment(CreateCommentRequest req, Long authorId) {
        log.info("User {} commenting on file {} (line {})", authorId, req.getFileId(), req.getLineNumber());

        Comment parent = null;
        if (req.getParentId() != null) {
            parent = findCommentOrThrow(req.getParentId());

            // Guard: only allow one level of replies (no reply-to-reply)
            if (parent.getParent() != null) {
                throw new InvalidCommentOperationException(
                        "Cannot reply to a reply. Only one level of threading is supported.");
            }
            // Guard: cannot reply to a deleted comment
            if (parent.isDeleted()) {
                throw new InvalidCommentOperationException(
                        "Cannot reply to a deleted comment.");
            }
        }

        Comment comment = Comment.builder()
                .fileId(req.getFileId())
                .projectId(req.getProjectId())
                .lineNumber(req.getLineNumber())
                .columnStart(req.getColumnStart())
                .columnEnd(req.getColumnEnd())
                .commitHash(req.getCommitHash())
                .body(req.getBody())
                .authorId(authorId)
                .parent(parent)
                .build();

        comment = commentRepository.save(comment);
        extractAndNotifyMentions(comment);
        CommentResponse response = toResponse(comment, true);
        broadcastEvent(comment.getProjectId(), "COMMENT_CREATED", response);
        log.info("Comment id={} created by user {}", comment.getId(), authorId);
        return response;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ── READ ────────────────────────────────────────────────────────────────
    // ═══════════════════════════════════════════════════════════════════════

    /** Get a single comment (with replies) by ID. */
    @Transactional(readOnly = true)
    public CommentResponse getComment(Long commentId) {
        Comment comment = findCommentOrThrow(commentId);
        return toResponse(comment, true);
    }

    /** Paginated root-level comments for a file (newest first). */
    @Transactional(readOnly = true)
    public Page<CommentResponse> getFileComments(Long fileId, Pageable pageable) {
        return commentRepository
                .findByFileIdAndParentIsNullAndDeletedFalseOrderByCreatedAtDesc(fileId, pageable)
                .map(c -> toResponse(c, true));
    }

    /** All comments on a specific line of a file (thread order). */
    @Transactional(readOnly = true)
    public List<CommentResponse> getLineComments(Long fileId, int lineNumber) {
        return commentRepository
                .findByFileIdAndLineNumberAndParentIsNullAndDeletedFalseOrderByCreatedAtAsc(fileId, lineNumber)
                .stream()
                .map(c -> toResponse(c, true))
                .collect(Collectors.toList());
    }

    /** Paginated project-level comment feed (all files, newest first). */
    @Transactional(readOnly = true)
    public Page<CommentResponse> getProjectComments(Long projectId, Pageable pageable) {
        return commentRepository
                .findByProjectIdAndParentIsNullAndDeletedFalseOrderByCreatedAtDesc(projectId, pageable)
                .map(c -> toResponse(c, false));
    }

    /** All unresolved comment threads for a file. */
    @Transactional(readOnly = true)
    public List<CommentResponse> getUnresolvedComments(Long fileId) {
        return commentRepository
                .findByFileIdAndParentIsNullAndResolvedFalseAndDeletedFalse(fileId)
                .stream()
                .map(c -> toResponse(c, true))
                .collect(Collectors.toList());
    }

    /** Keyword search across comment bodies in a project. */
    @Transactional(readOnly = true)
    public Page<CommentResponse> searchComments(Long projectId, String keyword, Pageable pageable) {
        return commentRepository
                .searchByKeyword(projectId, keyword, pageable)
                .map(c -> toResponse(c, false));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ── UPDATE ──────────────────────────────────────════════════════════════
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Edit a comment's body. Only the author can edit.
     */
    public CommentResponse updateComment(Long commentId, UpdateCommentRequest req, Long userId) {
        Comment comment = findCommentOrThrow(commentId);
        assertAuthor(comment, userId);
        if (comment.isDeleted()) {
            throw new InvalidCommentOperationException("Cannot edit a deleted comment.");
        }
        
        // Save history
        com.codesync.comment.model.CommentHistory history = com.codesync.comment.model.CommentHistory.builder()
                .comment(comment)
                .oldBody(comment.getBody())
                .editedBy(userId)
                .build();
        historyRepository.save(history);
        
        comment.setBody(req.getBody());
        comment.setEdited(true);
        comment = commentRepository.save(comment);
        extractAndNotifyMentions(comment);
        
        CommentResponse response = toResponse(comment, true);
        broadcastEvent(comment.getProjectId(), "COMMENT_UPDATED", response);
        log.info("Comment id={} updated by author {}", commentId, userId);
        return response;
    }

    /**
     * Resolve a comment thread (mark as done). Any project member can resolve.
     */
    public CommentResponse resolveComment(Long commentId, Long userId) {
        Comment comment = findCommentOrThrow(commentId);
        if (comment.getParent() != null) {
            throw new InvalidCommentOperationException("Only root comments can be resolved.");
        }
        if (comment.isResolved()) {
            throw new InvalidCommentOperationException("Comment is already resolved.");
        }
        comment.setResolved(true);
        comment.setResolvedBy(userId);
        comment.setResolvedAt(LocalDateTime.now());
        comment = commentRepository.save(comment);
        
        CommentResponse response = toResponse(comment, true);
        broadcastEvent(comment.getProjectId(), "COMMENT_RESOLVED", response);
        log.info("Comment id={} resolved by user {}", commentId, userId);
        return response;
    }

    /**
     * Reopen a resolved comment thread.
     */
    public CommentResponse reopenComment(Long commentId, Long userId) {
        Comment comment = findCommentOrThrow(commentId);
        if (!comment.isResolved()) {
            throw new InvalidCommentOperationException("Comment is not resolved.");
        }
        comment.setResolved(false);
        comment.setResolvedBy(null);
        comment.setResolvedAt(null);
        comment = commentRepository.save(comment);
        log.info("Comment id={} reopened by user {}", commentId, userId);
        return toResponse(comment, true);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ── DELETE ──────────────────────────────────────────────────────────────
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Soft-delete a comment. Only the author can delete.
     * Body is replaced with "[deleted]" placeholder on display.
     */
    public void deleteComment(Long commentId, Long userId) {
        Comment comment = findCommentOrThrow(commentId);
        assertAuthor(comment, userId);
        comment.setDeleted(true);
        comment.setBody("[deleted]");
        commentRepository.save(comment);
        log.info("Comment id={} soft-deleted by author {}", commentId, userId);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ── REACTIONS ───────────────────────────────────────────────────────────
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Add an emoji reaction to a comment.
     * A user can react with the same emoji only once.
     */
    public Map<String, Long> addReaction(Long commentId, String emoji, Long userId) {
        Comment comment = findCommentOrThrow(commentId);
        if (comment.isDeleted()) {
            throw new InvalidCommentOperationException("Cannot react to a deleted comment.");
        }
        if (reactionRepository.existsByCommentIdAndUserIdAndEmoji(commentId, userId, emoji)) {
            throw new DuplicateReactionException(
                    "You have already reacted with '" + emoji + "' on this comment.");
        }
        CommentReaction reaction = CommentReaction.builder()
                .comment(comment)
                .userId(userId)
                .emoji(emoji)
                .build();
        reactionRepository.save(reaction);
        log.info("User {} reacted {} on comment {}", userId, emoji, commentId);
        return getReactionCounts(commentId);
    }

    /**
     * Remove a user's emoji reaction from a comment.
     */
    public Map<String, Long> removeReaction(Long commentId, String emoji, Long userId) {
        if (!reactionRepository.existsByCommentIdAndUserIdAndEmoji(commentId, userId, emoji)) {
            throw new ResourceNotFoundException(
                    "Reaction '" + emoji + "' not found for this comment.");
        }
        reactionRepository.deleteByCommentIdAndUserIdAndEmoji(commentId, userId, emoji);
        log.info("User {} removed reaction {} from comment {}", userId, emoji, commentId);
        return getReactionCounts(commentId);
    }

    /**
     * Get all reaction counts for a comment.
     */
    @Transactional(readOnly = true)
    public Map<String, Long> getReactionCounts(Long commentId) {
        Map<String, Long> counts = new LinkedHashMap<>();
        reactionRepository.countByEmoji(commentId)
                .forEach(row -> counts.put((String) row[0], (Long) row[1]));
        return counts;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ── Private Helpers ─────────────────────────────────────────────────────
    // ═══════════════════════════════════════════════════════════════════════

    private Comment findCommentOrThrow(Long id) {
        return commentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Comment", "id", id));
    }

    private void assertAuthor(Comment comment, Long userId) {
        if (!comment.getAuthorId().equals(userId)) {
            throw new AccessDeniedException("Only the comment author can perform this action.");
        }
    }

    // ── Mappers ────────────────────────────────────────────────────────────

    private CommentResponse toResponse(Comment c, boolean withReplies) {
        List<CommentResponse> replies = null;
        if (withReplies && c.getParent() == null) {
            replies = commentRepository
                    .findByParentIdAndDeletedFalseOrderByCreatedAtAsc(c.getId())
                    .stream()
                    .map(r -> toResponse(r, false))
                    .collect(Collectors.toList());
        }

        Map<String, Long> reactions = reactionRepository.countByEmoji(c.getId())
                .stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0],
                        row -> (Long) row[1],
                        (a, b) -> a,
                        LinkedHashMap::new));

        return CommentResponse.builder()
                .id(c.getId())
                .fileId(c.getFileId())
                .projectId(c.getProjectId())
                .lineNumber(c.getLineNumber())
                .columnStart(c.getColumnStart())
                .columnEnd(c.getColumnEnd())
                .commitHash(c.getCommitHash())
                .body(c.isDeleted() ? "[deleted]" : c.getBody())
                .authorId(c.getAuthorId())
                .parentId(c.getParent() != null ? c.getParent().getId() : null)
                .resolved(c.isResolved())
                .resolvedBy(c.getResolvedBy())
                .resolvedAt(c.getResolvedAt())
                .deleted(c.isDeleted())
                .edited(c.isEdited())
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt())
                .replies(replies)
                .reactions(reactions)
                .build();
    }
}
