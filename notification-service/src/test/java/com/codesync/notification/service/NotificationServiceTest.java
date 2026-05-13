package com.codesync.notification.service;

import com.codesync.notification.dto.*;
import com.codesync.notification.exception.ResourceNotFoundException;
import com.codesync.notification.model.Notification;
import com.codesync.notification.model.NotificationPreference;
import com.codesync.notification.model.NotificationType;
import com.codesync.notification.repository.NotificationPreferenceRepository;
import com.codesync.notification.repository.NotificationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link NotificationService}.
 * Pure Mockito — no Spring context, no DB, no email sending.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationService Unit Tests")
class NotificationServiceTest {

    @Mock NotificationRepository           notificationRepository;
    @Mock NotificationPreferenceRepository preferenceRepository;
    @Mock EmailService                     emailService;
    @Mock org.springframework.messaging.simp.SimpMessagingTemplate messagingTemplate;
    @InjectMocks NotificationService       notificationService;

    private static final Long USER_ID  = 1L;
    private static final Long ACTOR_ID = 2L;
    private static final Long NOTIF_ID = 100L;

    private NotificationPreference defaultPrefs() {
        return NotificationPreference.builder().userId(USER_ID).build();
    }

    private Notification sampleNotification() {
        return Notification.builder()
                .id(NOTIF_ID).recipientId(USER_ID).actorId(ACTOR_ID)
                .type(NotificationType.COMMENT).title("New comment")
                .message("User2 commented on Auth.java")
                .read(false).deleted(false).emailSent(false).build();
    }

    private CreateNotificationRequest sampleRequest() {
        CreateNotificationRequest req = new CreateNotificationRequest();
        req.setRecipientId(USER_ID);
        req.setActorId(ACTOR_ID);
        req.setType("COMMENT"); 
        req.setTitle("New comment");
        req.setMessage("User2 commented on Auth.java");
        return req;
    }

    // ═══════════════════════════════════════════════════════════════════════
    @Nested @DisplayName("createNotification()")
    class CreateNotification {

        @Test @DisplayName("creates in-app notification with default prefs")
        void createsInAppNotification() {
            when(preferenceRepository.findById(USER_ID)).thenReturn(Optional.of(defaultPrefs()));
            when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> {
                Notification n = inv.getArgument(0);
                n = Notification.builder().id(NOTIF_ID).recipientId(n.getRecipientId())
                        .actorId(n.getActorId()).type(n.getType())
                        .title(n.getTitle()).message(n.getMessage())
                        .read(false).deleted(false).emailSent(false).build();
                return n;
            });

            NotificationResponse resp = notificationService.createNotification(sampleRequest(), null);

            assertThat(resp.getId()).isEqualTo(NOTIF_ID);
            assertThat(resp.getType()).isEqualTo(NotificationType.COMMENT);
            assertThat(resp.getTitle()).isEqualTo("New comment");
            verify(emailService, never()).sendNotificationEmail(any(), any()); // no email (null address)
        }

        @Test @DisplayName("sends email when email enabled and address provided")
        void sendsEmailWhenEnabled() {
            NotificationPreference prefs = defaultPrefs();
            prefs.setCommentEmail(true); // enable email for comments
            when(preferenceRepository.findById(USER_ID)).thenReturn(Optional.of(prefs));
            Notification saved = sampleNotification();
            when(notificationRepository.save(any(Notification.class))).thenReturn(saved);
            when(emailService.sendNotificationEmail(any(), eq("user@test.com"))).thenReturn(true);

            NotificationResponse resp = notificationService.createNotification(
                    sampleRequest(), "user@test.com");

            verify(emailService).sendNotificationEmail(any(), eq("user@test.com"));
        }

        @Test @DisplayName("creates default prefs if none exist")
        void createsDefaultPrefsIfAbsent() {
            when(preferenceRepository.findById(USER_ID)).thenReturn(Optional.empty());
            when(preferenceRepository.save(any(NotificationPreference.class))).thenAnswer(inv -> inv.getArgument(0));
            when(notificationRepository.save(any(Notification.class))).thenReturn(sampleNotification());

            notificationService.createNotification(sampleRequest(), null);

            verify(preferenceRepository).save(any(NotificationPreference.class));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    @Nested @DisplayName("markAsRead()")
    class MarkAsRead {

        @Test @DisplayName("marks an unread notification as read")
        void marksAsRead() {
            Notification n = sampleNotification();
            when(notificationRepository.findById(NOTIF_ID)).thenReturn(Optional.of(n));
            when(notificationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            NotificationResponse resp = notificationService.markAsRead(NOTIF_ID, USER_ID);

            assertThat(resp.isRead()).isTrue();
            assertThat(n.getReadAt()).isNotNull();
        }

        @Test @DisplayName("does not update already-read notification")
        void skipsAlreadyRead() {
            Notification n = sampleNotification();
            n.setRead(true);
            when(notificationRepository.findById(NOTIF_ID)).thenReturn(Optional.of(n));

            notificationService.markAsRead(NOTIF_ID, USER_ID);

            verify(notificationRepository, never()).save(any());
        }

        @Test @DisplayName("throws ResourceNotFoundException for wrong user")
        void throwsForWrongUser() {
            Notification n = sampleNotification();
            when(notificationRepository.findById(NOTIF_ID)).thenReturn(Optional.of(n));

            assertThatThrownBy(() -> notificationService.markAsRead(NOTIF_ID, 999L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    @Nested @DisplayName("deleteNotification()")
    class DeleteNotification {

        @Test @DisplayName("soft-deletes a notification")
        void softDeletes() {
            Notification n = sampleNotification();
            when(notificationRepository.findById(NOTIF_ID)).thenReturn(Optional.of(n));
            when(notificationRepository.save(any())).thenReturn(n);

            notificationService.deleteNotification(NOTIF_ID, USER_ID);

            assertThat(n.isDeleted()).isTrue();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    @Nested @DisplayName("getSummary()")
    class GetSummary {

        @Test @DisplayName("returns unread count and total")
        void returnsSummary() {
            when(notificationRepository.countByRecipientIdAndReadFalseAndDeletedFalse(USER_ID)).thenReturn(3L);
            when(notificationRepository.findByRecipientIdAndDeletedFalseOrderByCreatedAtDesc(
                    eq(USER_ID), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(sampleNotification(), sampleNotification())));

            NotificationSummary summary = notificationService.getSummary(USER_ID);

            assertThat(summary.getTotalUnread()).isEqualTo(3L);
            assertThat(summary.getTotalCount()).isEqualTo(2L);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    @Nested @DisplayName("updatePreferences()")
    class UpdatePreferences {

        @Test @DisplayName("partially updates preferences")
        void partiallyUpdatesPrefs() {
            NotificationPreference prefs = defaultPrefs();
            when(preferenceRepository.findById(USER_ID)).thenReturn(Optional.of(prefs));
            when(preferenceRepository.save(any(NotificationPreference.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            UpdatePreferenceRequest req = new UpdatePreferenceRequest();
            req.setCommentEmail(true);  // only change comment email
            req.setVersionEmail(true);  // and version email

            notificationService.updatePreferences(USER_ID, req);

            // Verify entity was mutated correctly
            assertThat(prefs.isCommentEmail()).isTrue();
            assertThat(prefs.isVersionEmail()).isTrue();
            // mentionEmail defaults to true and was not in the request — should stay true
            assertThat(prefs.isMentionEmail()).isTrue();
            // collabEmail defaults to false and was not in the request — should stay false
            assertThat(prefs.isCollabEmail()).isFalse();
        }
    }
}
