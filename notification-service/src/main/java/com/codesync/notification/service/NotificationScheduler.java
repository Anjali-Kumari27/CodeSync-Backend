package com.codesync.notification.service;

import com.codesync.notification.model.Notification;
import com.codesync.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationScheduler {

    private final NotificationRepository notificationRepository;
    private final NotificationService notificationService;

    // Run every day at 8 AM
    @Scheduled(cron = "0 0 8 * * *")
    @Transactional
    public void sendBatchedSummaries() {
        log.info("Running batched summary scheduler...");
        
        // Find all unread notifications, group by recipient
        // In a real scenario, this requires chunking/pagination
        List<Notification> unread = notificationRepository.findAll().stream()
                .filter(n -> !n.isRead() && !n.isDeleted() && !n.isEmailSent())
                .collect(Collectors.toList());
                
        Map<Long, List<Notification>> grouped = unread.stream()
                .collect(Collectors.groupingBy(Notification::getRecipientId));
                
        for (Map.Entry<Long, List<Notification>> entry : grouped.entrySet()) {
            Long userId = entry.getKey();
            List<Notification> notifs = entry.getValue();
            
            log.info("User {} has {} unread notifications. Generating summary...", userId, notifs.size());
            
            // In a real application, you'd fetch the user's email, compile a nice summary template,
            // and send it. Here we just log for demonstration purposes.
            // emailService.sendSummaryEmail(email, notifs);
            
            // Mark as email sent so we don't batch them again
            notifs.forEach(n -> n.setEmailSent(true));
            notificationRepository.saveAll(notifs);
        }
    }
}
