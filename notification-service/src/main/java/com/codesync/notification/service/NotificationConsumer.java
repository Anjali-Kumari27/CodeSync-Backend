package com.codesync.notification.service;

import com.codesync.notification.config.RabbitMQConfig;
import com.codesync.notification.dto.CreateNotificationRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationConsumer {

    private final NotificationService notificationService;

    @RabbitListener(queues = "notification_queue")
    public void consumeNotificationEvent(CreateNotificationRequest request) {
        try {
            log.info("Received notification event via RabbitMQ: {}", request.getTitle());

            if (request == null || request.getRecipientId() == null) {
                log.warn("Invalid notification request received: {}", request);
                return;
            }

            notificationService.createNotification(request, null);

        } catch (Exception e) {
            log.error("Notification processing failed: {}", e.getMessage());
        }
    }
}
