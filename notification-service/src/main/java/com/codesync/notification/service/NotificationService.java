package com.codesync.notification.service;

import com.codesync.notification.dto.*;
import com.codesync.notification.exception.ResourceNotFoundException;
import com.codesync.notification.model.Notification;
import com.codesync.notification.model.NotificationPreference;
import com.codesync.notification.model.NotificationType;
import com.codesync.notification.repository.NotificationPreferenceRepository;
import com.codesync.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Core business logic for the Notification Service.
 *
 * Responsibilities: 1. Create notifications triggered by other services. 2.
 * Respect user preferences (in-app/email toggles per type). 3. Deliver email
 * asynchronously via EmailService. 4. Provide inbox API: paginated, filtered,
 * with badge count. 5. Mark as read (single or all-at-once). 6. Soft-delete
 * notifications. 7. Manage per-user notification preferences.
 *
 * Email recipient lookup: In production, call the Auth Service to resolve
 * recipientId → email. For this implementation, we accept a recipientEmail
 * parameter in create. A dedicated user cache or Feign client would be added in
 * production.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class NotificationService {

	private final NotificationRepository notificationRepository;
	private final NotificationPreferenceRepository preferenceRepository;
	private final EmailService emailService;
	private final org.springframework.messaging.simp.SimpMessagingTemplate messagingTemplate;

	// ═══════════════════════════════════════════════════════════════════════
	// ── CREATE ──────────────────────────────────────────────────────────────
	// ═══════════════════════════════════════════════════════════════════════

	/**
	 * Create and deliver a notification.
	 *
	 * @param req            notification payload
	 * @param recipientEmail email address of recipient (null = skip email)
	 */
	public NotificationResponse createNotification(CreateNotificationRequest req, String recipientEmail) {

		if (req == null) {
			log.warn("Notification request is null");
			return null;
		}

		if (req.getRecipientId() == null) {
			log.warn("recipientId is null for notification title={}", req.getTitle());
			return null;
		}

		if (req.getType() == null || req.getType().isBlank()) {
			log.warn("notification type is null/blank");
			return null;
		}

		NotificationType type;
		try {
			type = NotificationType.valueOf(req.getType());
		} catch (Exception e) {
			log.warn("Invalid notification type: {}", req.getType());
			return null;
		}

// Load or create preferences
		NotificationPreference prefs = preferenceRepository.findById(req.getRecipientId()).orElseGet(() -> {
			NotificationPreference defaults = NotificationPreference.builder().userId(req.getRecipientId()).build();
			return preferenceRepository.save(defaults);
		});

		boolean inAppEnabled = isInAppEnabled(prefs, type);
		boolean emailEnabled = isEmailEnabled(prefs, type);

		if (!inAppEnabled && !emailEnabled) {
			log.debug("User {} disabled all channels for {}", req.getRecipientId(), req.getType());
		}

		Notification notification = Notification.builder().recipientId(req.getRecipientId()).actorId(req.getActorId())
				.type(type).title(req.getTitle()).message(req.getMessage()).actionUrl(req.getActionUrl())
				.referenceId(req.getReferenceId()).projectId(req.getProjectId()).build();

		notification = notificationRepository.save(notification);

		log.info("Notification created for user {}", req.getRecipientId());

		if (emailEnabled) {
			boolean sent = emailService.sendNotificationEmail(notification, recipientEmail);

			if (sent) {
				notification.setEmailSent(true);
				notification = notificationRepository.save(notification);
			}
		}

		NotificationResponse response = toResponse(notification);

		if (inAppEnabled) {
			messagingTemplate.convertAndSend("/topic/user/" + req.getRecipientId() + "/notifications", response);
		}

		return response;
	}

	// ═══════════════════════════════════════════════════════════════════════
	// ── INBOX READ ──────────────────────────────────────────────────────────
	// ═══════════════════════════════════════════════════════════════════════

	/** Paginated inbox for a user (all, newest first). */
	@Transactional(readOnly = true)
	public Page<NotificationResponse> getInbox(Long userId, Pageable pageable) {
		return notificationRepository.findByRecipientIdAndDeletedFalseOrderByCreatedAtDesc(userId, pageable)
				.map(this::toResponse);
	}

	/** Paginated unread notifications for a user. */
	@Transactional(readOnly = true)
	public Page<NotificationResponse> getUnread(Long userId, Pageable pageable) {
		return notificationRepository.findByRecipientIdAndReadFalseAndDeletedFalseOrderByCreatedAtDesc(userId, pageable)
				.map(this::toResponse);
	}

	/** Paginated notifications filtered by type. */
	@Transactional(readOnly = true)
	public Page<NotificationResponse> getByType(Long userId, NotificationType type, Pageable pageable) {
		return notificationRepository
				.findByRecipientIdAndTypeAndDeletedFalseOrderByCreatedAtDesc(userId, type, pageable)
				.map(this::toResponse);
	}

	/** Get a single notification by ID. */
	@Transactional(readOnly = true)
	public NotificationResponse getNotification(Long notifId, Long userId) {
		Notification n = findOrThrow(notifId);
		if (!n.getRecipientId().equals(userId)) {
			throw new ResourceNotFoundException("Notification", "id", notifId);
		}
		return toResponse(n);
	}

	/** Badge summary: unread count and total count for a user. */
	@Transactional(readOnly = true)
	public NotificationSummary getSummary(Long userId) {
		long unread = notificationRepository.countByRecipientIdAndReadFalseAndDeletedFalse(userId);
		long total = notificationRepository
				.findByRecipientIdAndDeletedFalseOrderByCreatedAtDesc(userId, Pageable.unpaged()).getTotalElements();
		return NotificationSummary.builder().totalUnread(unread).totalCount(total).build();
	}

	// ═══════════════════════════════════════════════════════════════════════
	// ── MARK AS READ ────────────────────────────────────────────────────────
	// ═══════════════════════════════════════════════════════════════════════

	/** Mark a single notification as read. */
	public NotificationResponse markAsRead(Long notifId, Long userId) {
		Notification n = findOrThrow(notifId);
		if (!n.getRecipientId().equals(userId)) {
			throw new ResourceNotFoundException("Notification", "id", notifId);
		}
		if (!n.isRead()) {
			n.setRead(true);
			n.setReadAt(LocalDateTime.now());
			n = notificationRepository.save(n);
		}
		return toResponse(n);
	}

	/** Mark all unread notifications as read for a user. */
	public int markAllAsRead(Long userId) {
		return notificationRepository.markAllAsRead(userId);
	}

	// ═══════════════════════════════════════════════════════════════════════
	// ── DELETE ──────────────────────────────────────────────────────────────
	// ═══════════════════════════════════════════════════════════════════════

	/** Soft-delete a notification. */
	public void deleteNotification(Long notifId, Long userId) {
		Notification n = findOrThrow(notifId);
		if (!n.getRecipientId().equals(userId)) {
			throw new ResourceNotFoundException("Notification", "id", notifId);
		}
		n.setDeleted(true);
		notificationRepository.save(n);
		log.info("Notification id={} soft-deleted by user {}", notifId, userId);
	}

	// ═══════════════════════════════════════════════════════════════════════
	// ── PREFERENCES ─────────────────────────────────────────────────────────
	// ═══════════════════════════════════════════════════════════════════════

	/** Get or create default preferences for a user. */
	@Transactional(readOnly = true)
	public PreferenceResponse getPreferences(Long userId) {
		return PreferenceResponse.from(preferenceRepository.findById(userId)
				.orElseGet(() -> NotificationPreference.builder().userId(userId).build()));
	}

	/** Partially update user preferences (only non-null fields are changed). */
	public PreferenceResponse updatePreferences(Long userId, UpdatePreferenceRequest req) {
		NotificationPreference prefs = preferenceRepository.findById(userId)
				.orElseGet(() -> NotificationPreference.builder().userId(userId).build());

		if (req.getCommentInApp() != null)
			prefs.setCommentInApp(req.getCommentInApp());
		if (req.getCommentEmail() != null)
			prefs.setCommentEmail(req.getCommentEmail());
		if (req.getMentionInApp() != null)
			prefs.setMentionInApp(req.getMentionInApp());
		if (req.getMentionEmail() != null)
			prefs.setMentionEmail(req.getMentionEmail());
		if (req.getVersionInApp() != null)
			prefs.setVersionInApp(req.getVersionInApp());
		if (req.getVersionEmail() != null)
			prefs.setVersionEmail(req.getVersionEmail());
		if (req.getExecutionInApp() != null)
			prefs.setExecutionInApp(req.getExecutionInApp());
		if (req.getExecutionEmail() != null)
			prefs.setExecutionEmail(req.getExecutionEmail());
		if (req.getCollabInApp() != null)
			prefs.setCollabInApp(req.getCollabInApp());
		if (req.getCollabEmail() != null)
			prefs.setCollabEmail(req.getCollabEmail());
		if (req.getProjectInApp() != null)
			prefs.setProjectInApp(req.getProjectInApp());
		if (req.getProjectEmail() != null)
			prefs.setProjectEmail(req.getProjectEmail());
		if (req.getSystemInApp() != null)
			prefs.setSystemInApp(req.getSystemInApp());
		if (req.getSystemEmail() != null)
			prefs.setSystemEmail(req.getSystemEmail());

		return PreferenceResponse.from(preferenceRepository.save(prefs));
	}

	// ── Private Helpers ─────────────────────────────────────────────────────

	private Notification findOrThrow(Long id) {
		return notificationRepository.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("Notification", "id", id));
	}

	private boolean isInAppEnabled(NotificationPreference p, NotificationType type) {
		return switch (type) {
		case COMMENT -> p.isCommentInApp();
		case MENTION -> p.isMentionInApp();
		case VERSION -> p.isVersionInApp();
		case EXECUTION -> p.isExecutionInApp();
		case COLLAB -> p.isCollabInApp();
		case PROJECT -> p.isProjectInApp();
		case SYSTEM -> p.isSystemInApp();
		};
	}

	private boolean isEmailEnabled(NotificationPreference p, NotificationType type) {
		return switch (type) {
		case COMMENT -> p.isCommentEmail();
		case MENTION -> p.isMentionEmail();
		case VERSION -> p.isVersionEmail();
		case EXECUTION -> p.isExecutionEmail();
		case COLLAB -> p.isCollabEmail();
		case PROJECT -> p.isProjectEmail();
		case SYSTEM -> p.isSystemEmail();
		};
	}

	private NotificationResponse toResponse(Notification n) {
		return NotificationResponse.builder().id(n.getId()).recipientId(n.getRecipientId()).actorId(n.getActorId())
				.type(n.getType()).title(n.getTitle()).message(n.getMessage()).actionUrl(n.getActionUrl())
				.referenceId(n.getReferenceId()).projectId(n.getProjectId()).read(n.isRead()).readAt(n.getReadAt())
				.emailSent(n.isEmailSent()).createdAt(n.getCreatedAt()).build();
	}
}
