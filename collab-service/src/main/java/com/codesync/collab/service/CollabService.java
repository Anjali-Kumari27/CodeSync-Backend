package com.codesync.collab.service;

import com.codesync.collab.dto.CreateSessionRequest;
import com.codesync.collab.dto.EditMessage;
import com.codesync.collab.dto.SessionEvent;
import com.codesync.collab.dto.SessionResponse;
import com.codesync.collab.exception.AccessDeniedException;
import com.codesync.collab.exception.ResourceNotFoundException;
import com.codesync.collab.exception.SessionNotActiveException;
import com.codesync.collab.model.CollabSession;
import com.codesync.collab.model.SessionParticipant;
import com.codesync.collab.model.SessionStatus;
import com.codesync.collab.repository.CollabSessionRepository;
import com.codesync.collab.repository.SessionParticipantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Core business logic for the Collaboration Service.
 *
 * Responsibilities: 1. Session lifecycle – create, join, leave, close sessions.
 * 2. Real-time fanout – broadcast EditMessage and SessionEvent over STOMP. 3.
 * Presence tracking – maintain list of active participants per session. 4.
 * Cursor tracking – sync live cursor / selection positions. 5. Typing indicator
 * – notify collaborators when someone is typing.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class CollabService {

	private final CollabSessionRepository sessionRepository;
	private final SessionParticipantRepository participantRepository;
	private final SimpMessagingTemplate messagingTemplate;

	// ═══════════════════════════════════════════════════════════════════════
	// ── SESSION LIFECYCLE ────────────────────────────────────────────────
	// ═══════════════════════════════════════════════════════════════════════

	/**
	 * Create a new session for a file, or return the existing ACTIVE session. Only
	 * one active session per file is allowed at a time.
	 */
	public SessionResponse createOrJoinSession(CreateSessionRequest req, Long userId) {
		return sessionRepository.findFirstByFileIdAndStatusOrderByCreatedAtDesc(req.getFileId(), SessionStatus.ACTIVE).map(existing -> {
			log.info("User {} joining existing session {} for file {}", userId, existing.getId(), req.getFileId());
			return joinSessionWithPassword(existing, userId, req.getPassword());
		}).orElseGet(() -> {
			log.info("User {} creating new session for file {} in project {}", userId, req.getFileId(),
					req.getProjectId());

			CollabSession session = CollabSession.builder().sessionKey(UUID.randomUUID().toString())
					.sessionName(req.getSessionName()).fileId(req.getFileId()).projectId(req.getProjectId())
					.createdBy(userId).ownerName("User-" + userId).language(req.getLanguage())
					.password(req.getPassword())
					.passwordProtected(req.getPassword() != null && !req.getPassword().isBlank())
					.maxParticipants(req.getMaxParticipants()).viewerModeAllowed(req.getViewerModeAllowed())
					.status(SessionStatus.ACTIVE).lastActivity(LocalDateTime.now()).build();

			session = sessionRepository.save(session);
			return joinSessionWithPassword(session, userId, req.getPassword());
		});
	}

	/**
	 * Validate password and participant capacity before allowing join.
	 */
	private SessionResponse joinSessionWithPassword(CollabSession session, Long userId, String providedPassword) {

		if (Boolean.TRUE.equals(session.getPasswordProtected())) {
			if (!session.getCreatedBy().equals(userId)
					&& (providedPassword == null || !providedPassword.equals(session.getPassword()))) {
				throw new AccessDeniedException("Invalid session password");
			}
		}

		long activeCount = participantRepository.countBySessionIdAndLeftAtIsNull(session.getId());

		if (session.getMaxParticipants() != null && session.getMaxParticipants() > 0) {
			boolean alreadyJoined = participantRepository.existsBySessionIdAndUserIdAndLeftAtIsNull(session.getId(),
					userId);

			if (!alreadyJoined && activeCount >= session.getMaxParticipants()
					&& !session.getCreatedBy().equals(userId)) {
				throw new AccessDeniedException("Session is full");
			}
		}

		return joinSession(session.getId(), userId);
	}

	/**
	 * Join an active collaboration session.
	 *
	 * A user can reconnect multiple times, but only one active participation row
	 * exists at a time.
	 */
	public SessionResponse joinSession(Long sessionId, Long userId) {
		CollabSession session = findSessionOrThrow(sessionId);
		assertSessionActive(session);

		boolean alreadyJoined = participantRepository.existsBySessionIdAndUserIdAndLeftAtIsNull(sessionId, userId);

		if (!alreadyJoined) {
			SessionParticipant participant = SessionParticipant.builder().session(session).userId(userId)
					.username("User-" + userId).avatarUrl(null)
					.role(session.getCreatedBy().equals(userId) ? "HOST" : "EDITOR").color(generateUserColor(userId))
					.cursorLine(1).cursorColumn(1).typing(false).active(true).lastHeartbeat(LocalDateTime.now())
					.build();

			participantRepository.save(participant);

			session.setParticipantCount(session.getParticipantCount() + 1);
			session.setActiveParticipants((int) participantRepository.countBySessionIdAndLeftAtIsNull(sessionId));
			session.setLastActivity(LocalDateTime.now());

			sessionRepository.save(session);

			broadcastSessionEvent(session, SessionEvent.EventType.USER_JOINED, userId, "joined the session");
		}

		return toSessionResponse(session);
	}

	/**
	 * Leave an active session.
	 *
	 * Marks participant row as inactive. If no active participants remain, session
	 * closes automatically.
	 */
	public void leaveSession(Long sessionId, Long userId) {
		CollabSession session = findSessionOrThrow(sessionId);

		participantRepository.findBySessionIdAndUserIdAndLeftAtIsNull(sessionId, userId).ifPresent(participant -> {
			participant.setLeftAt(LocalDateTime.now());
			participant.setActive(false);
			participantRepository.save(participant);
		});

		long remaining = participantRepository.countBySessionIdAndLeftAtIsNull(sessionId);

		session.setActiveParticipants((int) remaining);
		session.setLastActivity(LocalDateTime.now());
		sessionRepository.save(session);

		log.info("User {} left session {}. Remaining active: {}", userId, sessionId, remaining);

		broadcastSessionEvent(session, SessionEvent.EventType.USER_LEFT, userId, "left the session");

		if (remaining == 0 && session.getStatus() == SessionStatus.ACTIVE) {
			closeSession(sessionId, userId);
		}
	}

	/**
	 * Remove a participant from session.
	 *
	 * Only HOST can remove participants.
	 */
	public void kickParticipant(Long sessionId, Long targetUserId, Long requestingUserId) {
		CollabSession session = findSessionOrThrow(sessionId);
		assertSessionActive(session);

		if (!session.getCreatedBy().equals(requestingUserId)) {
			throw new AccessDeniedException("Only session creator can kick participants");
		}

		if (targetUserId.equals(requestingUserId)) {
			throw new IllegalArgumentException("Cannot kick yourself");
		}

		participantRepository.findBySessionIdAndUserIdAndLeftAtIsNull(sessionId, targetUserId)
				.ifPresent(participant -> {
					participant.setLeftAt(LocalDateTime.now());
					participant.setActive(false);
					participantRepository.save(participant);

					broadcastSessionEvent(session, SessionEvent.EventType.PARTICIPANT_KICKED, targetUserId,
							"removed from session");
				});
	}

	/**
	 * Cleanup job for abandoned sessions.
	 *
	 * Runs periodically and closes sessions that have no active participants.
	 */
	@Scheduled(fixedRate = 300000)
	public void cleanupInactiveSessions() {
		log.info("Running scheduled cleanup of inactive sessions");

		List<CollabSession> activeSessions = sessionRepository.findByStatus(SessionStatus.ACTIVE);

		for (CollabSession session : activeSessions) {
			long activeCount = participantRepository.countBySessionIdAndLeftAtIsNull(session.getId());

			if (activeCount == 0
			        && session.getLastActivity() != null
			        && session.getLastActivity().isBefore(LocalDateTime.now().minusMinutes(5))) {

				log.info("Auto-closing inactive session {}", session.getId());

				session.setStatus(SessionStatus.CLOSED);
				session.setClosedAt(LocalDateTime.now());

				sessionRepository.save(session);
			}
		}
	}

	/**
	 * Close collaboration session manually.
	 */
	public SessionResponse closeSession(Long sessionId, Long userId) {
		CollabSession session = findSessionOrThrow(sessionId);

		session.setStatus(SessionStatus.CLOSED);
		session.setClosedAt(LocalDateTime.now());

		session = sessionRepository.save(session);

		log.info("Session {} closed by user {}", sessionId, userId);

		broadcastSessionEvent(session, SessionEvent.EventType.SESSION_CLOSED, userId, "session closed");

		return toSessionResponse(session);
	}
	// ═══════════════════════════════════════════════════════════════════════
	// ── REAL-TIME EVENTS ─────────────────────────────────────────────────
	// ═══════════════════════════════════════════════════════════════════════

	/**
	 * Handle real-time edit operations.
	 *
	 * Updates session activity and broadcasts edit payload to all subscribers of
	 * the session topic.
	 */
	public void handleEdit(EditMessage message, Long userId) {
		CollabSession session = findSessionOrThrow(message.getSessionId());
		assertSessionActive(session);

		message.setUserId(userId);
		message.setTimestamp(System.currentTimeMillis());

		SessionParticipant participant = findParticipantOrThrow(message.getSessionId(), userId);

		participant.setLastHeartbeat(LocalDateTime.now());
		participantRepository.save(participant);

		session.setEditCount(session.getEditCount() + 1);
		session.setLastActivity(LocalDateTime.now());
		sessionRepository.save(session);

		messagingTemplate.convertAndSend("/topic/session/" + message.getSessionId(), message);

		log.debug("Edit broadcast on session {} by user {}", message.getSessionId(), userId);
	}

	/**
	 * Handle presence events.
	 *
	 * Broadcasts participant presence changes to all active collaborators.
	 */
	public void handlePresence(EditMessage message, Long userId) {
		CollabSession session = findSessionOrThrow(message.getSessionId());
		assertSessionActive(session);

		SessionParticipant participant = findParticipantOrThrow(message.getSessionId(), userId);

		participant.setLastHeartbeat(LocalDateTime.now());
		participant.setActive(true);
		participantRepository.save(participant);

		session.setLastActivity(LocalDateTime.now());
		sessionRepository.save(session);

		broadcastSessionEvent(session, SessionEvent.EventType.HEARTBEAT, userId, "presence updated");
	}

	/**
	 * Handle live cursor movement.
	 *
	 * Stores cursor line / column and broadcasts latest position.
	 */
	public void handleCursor(EditMessage message, Long userId) {
		CollabSession session = findSessionOrThrow(message.getSessionId());
		assertSessionActive(session);

		SessionParticipant participant = findParticipantOrThrow(message.getSessionId(), userId);

		participant.setCursorLine(message.getCursorLine());
		participant.setCursorColumn(message.getCursorColumn());
		participant.setLastHeartbeat(LocalDateTime.now());

		participantRepository.save(participant);

		message.setUserId(userId);
		message.setTimestamp(System.currentTimeMillis());

		messagingTemplate.convertAndSend("/topic/session/" + message.getSessionId(), message);
	}

	/**
	 * Handle text selection updates.
	 *
	 * Stores selected range and broadcasts selection highlight state.
	 */
	public void handleSelection(EditMessage message, Long userId) {
		CollabSession session = findSessionOrThrow(message.getSessionId());
		assertSessionActive(session);

		SessionParticipant participant = findParticipantOrThrow(message.getSessionId(), userId);

		participant.setSelectionStart(message.getSelectionStart());
		participant.setSelectionEnd(message.getSelectionEnd());
		participant.setLastHeartbeat(LocalDateTime.now());

		participantRepository.save(participant);

		message.setUserId(userId);
		message.setTimestamp(System.currentTimeMillis());

		messagingTemplate.convertAndSend("/topic/session/" + message.getSessionId(), message);
	}

	/**
	 * Handle typing indicator state.
	 *
	 * Broadcasts whether a user is currently typing.
	 */
	public void handleTyping(EditMessage message, Long userId) {
		CollabSession session = findSessionOrThrow(message.getSessionId());
		assertSessionActive(session);

		SessionParticipant participant = findParticipantOrThrow(message.getSessionId(), userId);

		participant.setTyping(Boolean.TRUE.equals(message.getTyping()));
		participant.setLastHeartbeat(LocalDateTime.now());

		participantRepository.save(participant);

		message.setUserId(userId);
		message.setTimestamp(System.currentTimeMillis());

		messagingTemplate.convertAndSend("/topic/session/" + message.getSessionId(), message);
	}

	/**
	 * Handle heartbeat ping from client.
	 *
	 * Keeps participant marked active and updates last heartbeat timestamp.
	 */
	public void handleHeartbeat(EditMessage message, Long userId) {
	    SessionParticipant participant =
	            findParticipantOrThrow(message.getSessionId(), userId);

	    participant.setActive(true);
	    participant.setLastHeartbeat(LocalDateTime.now());
	    participantRepository.save(participant);

	    CollabSession session =
	            findSessionOrThrow(message.getSessionId());

	    session.setLastActivity(LocalDateTime.now());
	    sessionRepository.save(session);
	}

	/**
	 * Handle collaborative save event.
	 *
	 * Broadcasts save notification to all connected participants.
	 */
	public void handleSave(EditMessage message, Long userId) {
		CollabSession session = findSessionOrThrow(message.getSessionId());
		assertSessionActive(session);

		session.setLastActivity(LocalDateTime.now());
		sessionRepository.save(session);

		message.setUserId(userId);
		message.setTimestamp(System.currentTimeMillis());

		messagingTemplate.convertAndSend("/topic/session/" + message.getSessionId(), message);
	}

	/**
	 * Handle live comment event.
	 *
	 * Broadcasts comment notification to all connected participants.
	 */
	public void handleComment(EditMessage message, Long userId) {
		CollabSession session = findSessionOrThrow(message.getSessionId());
		assertSessionActive(session);

		message.setUserId(userId);
		message.setTimestamp(System.currentTimeMillis());

		messagingTemplate.convertAndSend("/topic/session/" + message.getSessionId(), message);
	}

	// ═══════════════════════════════════════════════════════════════════════
	// ── HELPERS ──────────────────────────────────────────────────────────
	// ═══════════════════════════════════════════════════════════════════════

    /**
     * Get session details by ID.
     */
    @Transactional(readOnly = true)
    public SessionResponse getSession(Long sessionId) {
        return toSessionResponse(findSessionOrThrow(sessionId));
    }

    /**
     * Get active session for a file.
     */
    @Transactional(readOnly = true)
    public SessionResponse getActiveSessionForFile(Long fileId) {
        CollabSession session = sessionRepository
                .findFirstByFileIdAndStatusOrderByCreatedAtDesc(fileId, SessionStatus.ACTIVE)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "No active session found for file " + fileId));

        return toSessionResponse(session);
    }

    /**
     * Get all active sessions in a project.
     */
    @Transactional(readOnly = true)
    public List<SessionResponse> getProjectActiveSessions(Long projectId) {
        return sessionRepository
                .findByProjectIdAndStatus(projectId, SessionStatus.ACTIVE)
                .stream()
                .map(this::toSessionResponse)
                .collect(Collectors.toList());
    }
	/**
	 * Find session by id or throw exception.
	 */
	private CollabSession findSessionOrThrow(Long id) {
		return sessionRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Session", "id", id));
	}

	/**
	 * Find active participant row for a user.
	 */
	private SessionParticipant findParticipantOrThrow(Long sessionId, Long userId) {
		return participantRepository.findBySessionIdAndUserIdAndLeftAtIsNull(sessionId, userId)
				.orElseThrow(() -> new ResourceNotFoundException("Participant not found in active session"));
	}

	/**
	 * Validate session is active.
	 */
	private void assertSessionActive(CollabSession session) {
		if (session.getStatus() != SessionStatus.ACTIVE) {
			throw new SessionNotActiveException("Session " + session.getId() + " is closed.");
		}
	}

	/**
	 * Generate deterministic user color.
	 */
	private String generateUserColor(Long userId) {
		String[] colors = { "#ef4444", "#3b82f6", "#22c55e", "#f59e0b", "#8b5cf6", "#ec4899" };

		return colors[(int) (userId % colors.length)];
	}

	/**
	 * Get active participant IDs.
	 */
	@Transactional(readOnly = true)
	public List<Long> getActiveParticipants(Long sessionId) {
		return participantRepository.findBySessionIdAndLeftAtIsNull(sessionId).stream()
				.map(SessionParticipant::getUserId).collect(Collectors.toList());
	}

	/**
	 * Broadcast session-level events.
	 */
	private void broadcastSessionEvent(CollabSession session, SessionEvent.EventType type, Long userId,
			String message) {

		List<Long> participants = getActiveParticipants(session.getId());

		SessionEvent event = SessionEvent.builder().sessionId(session.getId()).eventType(type).userId(userId)
				.participants(participants).activeCount(participants.size()).message(message)
				.timestamp(LocalDateTime.now()).build();

		messagingTemplate.convertAndSend("/topic/session/" + session.getId(), event);
	}

	/**
	 * Convert entity to response DTO.
	 */
	private SessionResponse toSessionResponse(CollabSession session) {
		List<Long> activeParticipants = getActiveParticipants(session.getId());

		return SessionResponse.builder().id(session.getId()).sessionKey(session.getSessionKey())
				.sessionName(session.getSessionName()).fileId(session.getFileId()).projectId(session.getProjectId())
				.createdBy(session.getCreatedBy()).ownerName(session.getOwnerName()).language(session.getLanguage())
				.status(session.getStatus()).editCount(session.getEditCount())
				.participantCount(session.getParticipantCount()).activeParticipants(session.getActiveParticipants())
				.activeParticipantIds(activeParticipants).maxParticipants(session.getMaxParticipants())
				.hasPassword(Boolean.TRUE.equals(session.getPasswordProtected()))
				.viewerModeAllowed(session.getViewerModeAllowed()).lastActivity(session.getLastActivity())
				.createdAt(session.getCreatedAt()).closedAt(session.getClosedAt()).build();
	}
}