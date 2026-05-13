package com.codesync.comment.service;

import com.codesync.comment.dto.*;
import com.codesync.comment.exception.*;
import com.codesync.comment.model.Comment;
import com.codesync.comment.model.CommentReaction;
import com.codesync.comment.repository.CommentReactionRepository;
import com.codesync.comment.repository.CommentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link CommentService}.
 * Pure Mockito – no Spring context, no DB.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CommentService Unit Tests")
class CommentServiceTest {

    @Mock CommentRepository         commentRepository;
    @Mock CommentReactionRepository reactionRepository;
    @Mock com.codesync.comment.repository.CommentHistoryRepository historyRepository;
    @Mock org.springframework.messaging.simp.SimpMessagingTemplate messagingTemplate;
    @InjectMocks CommentService     commentService;

    private static final Long USER_ID      = 1L;
    private static final Long OTHER_USER   = 2L;
    private static final Long FILE_ID      = 10L;
    private static final Long PROJECT_ID   = 20L;
    private static final Long COMMENT_ID   = 100L;

    private Comment sampleComment() {
        return Comment.builder()
                .id(COMMENT_ID).fileId(FILE_ID).projectId(PROJECT_ID)
                .body("Looks great!").authorId(USER_ID)
                .resolved(false).deleted(false).edited(false).build();
    }

    private Comment sampleReply(Comment parent) {
        return Comment.builder()
                .id(200L).fileId(FILE_ID).projectId(PROJECT_ID)
                .body("Thanks!").authorId(OTHER_USER)
                .parent(parent).resolved(false).deleted(false).edited(false).build();
    }

    // ═══════════════════════════════════════════════════════════════════════
    @Nested @DisplayName("createComment()")
    class CreateComment {

        @Test @DisplayName("creates a root comment successfully")
        void createsRootComment() {
            CreateCommentRequest req = new CreateCommentRequest();
            req.setFileId(FILE_ID);
            req.setProjectId(PROJECT_ID);
            req.setBody("LGTM!");

            when(commentRepository.save(any(Comment.class))).thenAnswer(inv -> {
                Comment c = inv.getArgument(0);
                c = Comment.builder().id(COMMENT_ID).fileId(c.getFileId())
                        .projectId(c.getProjectId()).body(c.getBody())
                        .authorId(USER_ID).deleted(false).resolved(false).edited(false).build();
                return c;
            });
            when(commentRepository.findByParentIdAndDeletedFalseOrderByCreatedAtAsc(COMMENT_ID))
                    .thenReturn(List.of());
            when(reactionRepository.countByEmoji(COMMENT_ID)).thenReturn(List.of());

            CommentResponse resp = commentService.createComment(req, USER_ID);

            assertThat(resp.getBody()).isEqualTo("LGTM!");
            assertThat(resp.getAuthorId()).isEqualTo(USER_ID);
            assertThat(resp.getParentId()).isNull();
        }

        @Test @DisplayName("creates a reply to a root comment")
        void createsReply() {
            Comment root = sampleComment();
            CreateCommentRequest req = new CreateCommentRequest();
            req.setFileId(FILE_ID);
            req.setProjectId(PROJECT_ID);
            req.setBody("Thanks!");
            req.setParentId(COMMENT_ID);

            when(commentRepository.findById(COMMENT_ID)).thenReturn(Optional.of(root));
            when(commentRepository.save(any(Comment.class))).thenAnswer(inv -> {
                Comment c = inv.getArgument(0);
                c = Comment.builder().id(200L).fileId(FILE_ID).projectId(PROJECT_ID)
                        .body("Thanks!").authorId(OTHER_USER).parent(root)
                        .deleted(false).resolved(false).edited(false).build();
                return c;
            });
            when(reactionRepository.countByEmoji(200L)).thenReturn(List.of());

            CommentResponse resp = commentService.createComment(req, OTHER_USER);

            assertThat(resp.getParentId()).isEqualTo(COMMENT_ID);
        }

        @Test @DisplayName("throws InvalidCommentOperationException for reply-to-reply")
        void throwsForReplyToReply() {
            Comment root  = sampleComment();
            Comment reply = sampleReply(root);

            CreateCommentRequest req = new CreateCommentRequest();
            req.setFileId(FILE_ID);
            req.setProjectId(PROJECT_ID);
            req.setBody("Nested");
            req.setParentId(reply.getId());

            when(commentRepository.findById(reply.getId())).thenReturn(Optional.of(reply));

            assertThatThrownBy(() -> commentService.createComment(req, USER_ID))
                    .isInstanceOf(InvalidCommentOperationException.class)
                    .hasMessageContaining("reply to a reply");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    @Nested @DisplayName("updateComment()")
    class UpdateComment {

        @Test @DisplayName("author can edit their comment")
        void authorCanEdit() {
            Comment comment = sampleComment();
            when(commentRepository.findById(COMMENT_ID)).thenReturn(Optional.of(comment));
            when(commentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(commentRepository.findByParentIdAndDeletedFalseOrderByCreatedAtAsc(COMMENT_ID))
                    .thenReturn(List.of());
            when(reactionRepository.countByEmoji(COMMENT_ID)).thenReturn(List.of());

            UpdateCommentRequest req = new UpdateCommentRequest();
            req.setBody("Actually, fix this.");

            CommentResponse resp = commentService.updateComment(COMMENT_ID, req, USER_ID);

            assertThat(resp.getBody()).isEqualTo("Actually, fix this.");
            assertThat(resp.isEdited()).isTrue();
        }

        @Test @DisplayName("throws AccessDeniedException for non-author")
        void throwsForNonAuthor() {
            Comment comment = sampleComment();
            when(commentRepository.findById(COMMENT_ID)).thenReturn(Optional.of(comment));

            UpdateCommentRequest req = new UpdateCommentRequest();
            req.setBody("Changed");

            assertThatThrownBy(() -> commentService.updateComment(COMMENT_ID, req, OTHER_USER))
                    .isInstanceOf(AccessDeniedException.class);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    @Nested @DisplayName("resolveComment() / reopenComment()")
    class ResolveThread {

        @Test @DisplayName("resolves an open root comment")
        void resolvesComment() {
            Comment comment = sampleComment();
            when(commentRepository.findById(COMMENT_ID)).thenReturn(Optional.of(comment));
            when(commentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(commentRepository.findByParentIdAndDeletedFalseOrderByCreatedAtAsc(COMMENT_ID))
                    .thenReturn(List.of());
            when(reactionRepository.countByEmoji(COMMENT_ID)).thenReturn(List.of());

            CommentResponse resp = commentService.resolveComment(COMMENT_ID, USER_ID);

            assertThat(resp.isResolved()).isTrue();
            assertThat(resp.getResolvedBy()).isEqualTo(USER_ID);
        }

        @Test @DisplayName("throws when trying to resolve a reply")
        void throwsForReplyResolve() {
            Comment root  = sampleComment();
            Comment reply = sampleReply(root);
            when(commentRepository.findById(reply.getId())).thenReturn(Optional.of(reply));

            assertThatThrownBy(() -> commentService.resolveComment(reply.getId(), USER_ID))
                    .isInstanceOf(InvalidCommentOperationException.class)
                    .hasMessageContaining("root");
        }

        @Test @DisplayName("reopens a resolved comment")
        void reopensComment() {
            Comment comment = sampleComment();
            comment.setResolved(true);
            comment.setResolvedBy(USER_ID);
            when(commentRepository.findById(COMMENT_ID)).thenReturn(Optional.of(comment));
            when(commentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(commentRepository.findByParentIdAndDeletedFalseOrderByCreatedAtAsc(COMMENT_ID))
                    .thenReturn(List.of());
            when(reactionRepository.countByEmoji(COMMENT_ID)).thenReturn(List.of());

            CommentResponse resp = commentService.reopenComment(COMMENT_ID, OTHER_USER);

            assertThat(resp.isResolved()).isFalse();
            assertThat(resp.getResolvedBy()).isNull();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    @Nested @DisplayName("deleteComment()")
    class DeleteComment {

        @Test @DisplayName("author can soft-delete their comment")
        void authorCanDelete() {
            Comment comment = sampleComment();
            when(commentRepository.findById(COMMENT_ID)).thenReturn(Optional.of(comment));
            when(commentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            commentService.deleteComment(COMMENT_ID, USER_ID);

            assertThat(comment.isDeleted()).isTrue();
            assertThat(comment.getBody()).isEqualTo("[deleted]");
        }

        @Test @DisplayName("throws AccessDeniedException for non-author")
        void throwsForNonAuthor() {
            Comment comment = sampleComment();
            when(commentRepository.findById(COMMENT_ID)).thenReturn(Optional.of(comment));

            assertThatThrownBy(() -> commentService.deleteComment(COMMENT_ID, OTHER_USER))
                    .isInstanceOf(AccessDeniedException.class);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    @Nested @DisplayName("addReaction() / removeReaction()")
    class Reactions {

        @Test @DisplayName("adds a new emoji reaction")
        void addsReaction() {
            Comment comment = sampleComment();
            when(commentRepository.findById(COMMENT_ID)).thenReturn(Optional.of(comment));
            when(reactionRepository.existsByCommentIdAndUserIdAndEmoji(COMMENT_ID, USER_ID, "👍"))
                    .thenReturn(false);
            when(reactionRepository.save(any(CommentReaction.class))).thenAnswer(inv -> inv.getArgument(0));
            when(reactionRepository.countByEmoji(COMMENT_ID))
                    .thenReturn(List.<Object[]>of(new Object[]{"👍", 1L}));

            Map<String, Long> counts = commentService.addReaction(COMMENT_ID, "👍", USER_ID);

            assertThat(counts).containsEntry("👍", 1L);
        }

        @Test @DisplayName("throws DuplicateReactionException for duplicate emoji")
        void throwsForDuplicateReaction() {
            Comment comment = sampleComment();
            when(commentRepository.findById(COMMENT_ID)).thenReturn(Optional.of(comment));
            when(reactionRepository.existsByCommentIdAndUserIdAndEmoji(COMMENT_ID, USER_ID, "👍"))
                    .thenReturn(true);

            assertThatThrownBy(() -> commentService.addReaction(COMMENT_ID, "👍", USER_ID))
                    .isInstanceOf(DuplicateReactionException.class)
                    .hasMessageContaining("👍");
        }

        @Test @DisplayName("removes an existing reaction")
        void removesReaction() {
            when(reactionRepository.existsByCommentIdAndUserIdAndEmoji(COMMENT_ID, USER_ID, "👍"))
                    .thenReturn(true);
            when(reactionRepository.countByEmoji(COMMENT_ID))
                    .thenReturn(List.<Object[]>of());

            Map<String, Long> counts = commentService.removeReaction(COMMENT_ID, "👍", USER_ID);

            verify(reactionRepository).deleteByCommentIdAndUserIdAndEmoji(COMMENT_ID, USER_ID, "👍");
            assertThat(counts).isEmpty();
        }

        @Test @DisplayName("throws ResourceNotFoundException when reaction not found")
        void throwsWhenReactionNotFound() {
            when(reactionRepository.existsByCommentIdAndUserIdAndEmoji(COMMENT_ID, USER_ID, "❤️"))
                    .thenReturn(false);

            assertThatThrownBy(() -> commentService.removeReaction(COMMENT_ID, "❤️", USER_ID))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }
}
