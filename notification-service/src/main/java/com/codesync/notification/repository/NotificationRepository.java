package com.codesync.notification.repository;

import com.codesync.notification.model.Notification;
import com.codesync.notification.model.NotificationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for {@link Notification}.
 */
@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /** All undeleted notifications for a user, newest first (paginated). */
    Page<Notification> findByRecipientIdAndDeletedFalseOrderByCreatedAtDesc(
            Long recipientId, Pageable pageable);

    /** Only unread notifications for a user. */
    Page<Notification> findByRecipientIdAndReadFalseAndDeletedFalseOrderByCreatedAtDesc(
            Long recipientId, Pageable pageable);

    /** Notifications filtered by type for a user. */
    Page<Notification> findByRecipientIdAndTypeAndDeletedFalseOrderByCreatedAtDesc(
            Long recipientId, NotificationType type, Pageable pageable);

    /** Count unread notifications for a user (for badge count). */
    long countByRecipientIdAndReadFalseAndDeletedFalse(Long recipientId);

    /** Mark all unread notifications as read for a user. */
    @Modifying
    @Query("""
        UPDATE Notification n SET n.read = true, n.readAt = CURRENT_TIMESTAMP
        WHERE n.recipientId = :userId AND n.read = false AND n.deleted = false
        """)
    int markAllAsRead(@Param("userId") Long userId);

    /** Soft-delete all notifications for a user (e.g. on account deletion). */
    @Modifying
    @Query("UPDATE Notification n SET n.deleted = true WHERE n.recipientId = :userId")
    int softDeleteAllForUser(@Param("userId") Long userId);
}
