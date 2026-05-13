package com.codesync.collab.service;

import com.codesync.collab.dto.*;
import com.codesync.collab.exception.ResourceNotFoundException;
import com.codesync.collab.exception.SessionNotActiveException;
import com.codesync.collab.model.CollabSession;
import com.codesync.collab.model.SessionParticipant;
import com.codesync.collab.model.SessionStatus;
import com.codesync.collab.repository.CollabSessionRepository;
import com.codesync.collab.repository.SessionParticipantRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link CollabService}.
 * Pure Mockito – no Spring, no WebSocket.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CollabService Unit Tests")
class CollabServiceTest {

    @Mock CollabSessionRepository      sessionRepository;
    @Mock SessionParticipantRepository participantRepository;
    @Mock SimpMessagingTemplate        messagingTemplate;
    @InjectMocks CollabService         collabService;

    private static final Long USER_ID    = 1L;
    private static final Long FILE_ID    = 10L;
    private static final Long PROJECT_ID = 20L;
    private static final Long SESSION_ID = 100L;

    private CollabSession activeSession() {
        return CollabSession.builder()
                .id(SESSION_ID).fileId(FILE_ID).projectId(PROJECT_ID)
                .createdBy(USER_ID).status(SessionStatus.ACTIVE)
                .editCount(0L).participantCount(0L).build();
    }

    private CollabSession closedSession() {
        return CollabSession.builder()
                .id(SESSION_ID).fileId(FILE_ID).projectId(PROJECT_ID)
                .createdBy(USER_ID).status(SessionStatus.CLOSED)
                .editCount(5L).participantCount(2L).build();
    }

    // ═══════════════════════════════════════════════════════════════════════
    @Nested @DisplayName("createOrJoinSession()")
    class CreateOrJoin {

        @Test @DisplayName("creates a new session when none active")
        void createsNewSession() {
            CreateSessionRequest req = new CreateSessionRequest();
            req.setFileId(FILE_ID);
            req.setProjectId(PROJECT_ID);

            when(sessionRepository.findByFileIdAndStatus(FILE_ID, SessionStatus.ACTIVE))
                    .thenReturn(Optional.empty());
            when(sessionRepository.save(any(CollabSession.class))).thenAnswer(inv -> {
                CollabSession s = inv.getArgument(0);
                s = CollabSession.builder().id(SESSION_ID).fileId(s.getFileId())
                        .projectId(s.getProjectId()).createdBy(USER_ID)
                        .status(SessionStatus.ACTIVE).editCount(0L).participantCount(0L).build();
                return s;
            });
            // For joinSession inner call
            when(participantRepository.existsBySessionIdAndUserIdAndLeftAtIsNull(SESSION_ID, USER_ID))
                    .thenReturn(false);
            when(participantRepository.save(any(SessionParticipant.class))).thenAnswer(inv -> inv.getArgument(0));
            when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(activeSession()));
            when(participantRepository.findBySessionIdAndLeftAtIsNull(SESSION_ID)).thenReturn(List.of());
            doNothing().when(messagingTemplate).convertAndSend(anyString(), any(Object.class));

            SessionResponse resp = collabService.createOrJoinSession(req, USER_ID);

            assertThat(resp.getFileId()).isEqualTo(FILE_ID);
            assertThat(resp.getStatus()).isEqualTo(SessionStatus.ACTIVE);
        }

        @Test @DisplayName("returns existing active session if one exists")
        void returnsExistingSession() {
            CreateSessionRequest req = new CreateSessionRequest();
            req.setFileId(FILE_ID);
            req.setProjectId(PROJECT_ID);

            CollabSession existing = activeSession();
            when(sessionRepository.findByFileIdAndStatus(FILE_ID, SessionStatus.ACTIVE))
                    .thenReturn(Optional.of(existing));
            when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(existing));
            when(participantRepository.existsBySessionIdAndUserIdAndLeftAtIsNull(SESSION_ID, USER_ID))
                    .thenReturn(true); // already in session
            when(participantRepository.findBySessionIdAndLeftAtIsNull(SESSION_ID)).thenReturn(List.of());

            SessionResponse resp = collabService.createOrJoinSession(req, USER_ID);

            assertThat(resp.getId()).isEqualTo(SESSION_ID);
            // should NOT create a new participant since already active
            verify(participantRepository, never()).save(any());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    @Nested @DisplayName("leaveSession()")
    class LeaveSession {

        @Test @DisplayName("auto-closes session when last participant leaves")
        void autoClosesWhenEmpty() {
            CollabSession session = activeSession();
            SessionParticipant participant = SessionParticipant.builder()
                    .id(1L).session(session).userId(USER_ID).build();

            // findById called twice: once for leaveSession, once for closeSession
            when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));
            when(participantRepository.findBySessionIdAndUserIdAndLeftAtIsNull(SESSION_ID, USER_ID))
                    .thenReturn(Optional.of(participant));
            when(participantRepository.save(any())).thenReturn(participant);
            when(participantRepository.countBySessionIdAndLeftAtIsNull(SESSION_ID)).thenReturn(0L);
            when(participantRepository.findBySessionIdAndLeftAtIsNull(SESSION_ID)).thenReturn(List.of());
            when(sessionRepository.save(any(CollabSession.class))).thenReturn(session);
            doNothing().when(messagingTemplate).convertAndSend(anyString(), any(Object.class));

            collabService.leaveSession(SESSION_ID, USER_ID);

            // Session should be closed
            assertThat(session.getStatus()).isEqualTo(SessionStatus.CLOSED);
            assertThat(session.getClosedAt()).isNotNull();
        }

        @Test @DisplayName("does not close session when other participants remain")
        void doesNotCloseWhenOthersPresent() {
            CollabSession session = activeSession();
            session.setParticipantCount(2L);
            SessionParticipant participant = SessionParticipant.builder()
                    .id(1L).session(session).userId(USER_ID).build();

            when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));
            when(participantRepository.findBySessionIdAndUserIdAndLeftAtIsNull(SESSION_ID, USER_ID))
                    .thenReturn(Optional.of(participant));
            when(participantRepository.save(any())).thenReturn(participant);
            when(participantRepository.countBySessionIdAndLeftAtIsNull(SESSION_ID)).thenReturn(1L); // still 1 left
            when(participantRepository.findBySessionIdAndLeftAtIsNull(SESSION_ID)).thenReturn(List.of());
            doNothing().when(messagingTemplate).convertAndSend(anyString(), any(Object.class));

            collabService.leaveSession(SESSION_ID, USER_ID);

            assertThat(session.getStatus()).isEqualTo(SessionStatus.ACTIVE);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    @Nested @DisplayName("closeSession()")
    class CloseSession {

        @Test @DisplayName("closes an active session")
        void closesSession() {
            CollabSession session = activeSession();
            when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));
            when(sessionRepository.save(any())).thenReturn(session);
            when(participantRepository.findBySessionIdAndLeftAtIsNull(SESSION_ID)).thenReturn(List.of());
            doNothing().when(messagingTemplate).convertAndSend(anyString(), any(Object.class));

            SessionResponse resp = collabService.closeSession(SESSION_ID, USER_ID);

            assertThat(resp.getStatus()).isEqualTo(SessionStatus.CLOSED);
            assertThat(session.getClosedAt()).isNotNull();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    @Nested @DisplayName("handleEdit()")
    class HandleEdit {

        @Test @DisplayName("broadcasts edit and increments edit count")
        void broadcastsEdit() {
            CollabSession session = activeSession();
            when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));
            when(sessionRepository.save(any())).thenReturn(session);
            doNothing().when(messagingTemplate).convertAndSend(anyString(), any(Object.class));

            EditMessage msg = new EditMessage();
            msg.setSessionId(SESSION_ID);
            msg.setType(EditMessage.EditType.INSERT);
            msg.setOffset(0);
            msg.setText("Hello");

            collabService.handleEdit(msg, USER_ID);

            assertThat(session.getEditCount()).isEqualTo(1L);
            assertThat(msg.getUserId()).isEqualTo(USER_ID); // server stamps the userId
            verify(messagingTemplate).convertAndSend(
                    eq("/topic/session/" + SESSION_ID), any(Object.class));
        }

        @Test @DisplayName("throws SessionNotActiveException for closed session")
        void throwsForClosedSession() {
            when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(closedSession()));

            EditMessage msg = new EditMessage();
            msg.setSessionId(SESSION_ID);
            msg.setType(EditMessage.EditType.INSERT);

            assertThatThrownBy(() -> collabService.handleEdit(msg, USER_ID))
                    .isInstanceOf(SessionNotActiveException.class);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    @Nested @DisplayName("getActiveSessionForFile()")
    class GetActiveSession {

        @Test @DisplayName("returns active session for a file")
        void returnsSession() {
            when(sessionRepository.findByFileIdAndStatus(FILE_ID, SessionStatus.ACTIVE))
                    .thenReturn(Optional.of(activeSession()));
            when(participantRepository.findBySessionIdAndLeftAtIsNull(SESSION_ID)).thenReturn(List.of());

            SessionResponse resp = collabService.getActiveSessionForFile(FILE_ID);

            assertThat(resp.getFileId()).isEqualTo(FILE_ID);
        }

        @Test @DisplayName("throws ResourceNotFoundException when no active session")
        void throwsWhenNoSession() {
            when(sessionRepository.findByFileIdAndStatus(FILE_ID, SessionStatus.ACTIVE))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> collabService.getActiveSessionForFile(FILE_ID))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining(FILE_ID.toString());
        }
    }
}
