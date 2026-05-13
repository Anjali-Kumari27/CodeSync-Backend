package com.codesync.notification.service;

import com.codesync.notification.model.Notification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * EmailService – delivers notification emails via JavaMailSender (SMTP).
 *
 * Emails are sent asynchronously so they never block the main request thread.
 * Configure SMTP credentials in application.yml or via environment variables:
 *   SPRING_MAIL_USERNAME  / SPRING_MAIL_PASSWORD
 *
 * For production:
 *  - Replace SimpleMailMessage with MimeMessage + HTML templates (Thymeleaf).
 *  - Add retry logic with exponential back-off.
 *  - Consider a dedicated email provider (SendGrid, AWS SES, Mailgun).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;
    private final org.thymeleaf.TemplateEngine templateEngine;

    /**
     * Send an HTML email for a notification.
     */
    @Async
    public boolean sendNotificationEmail(Notification notification, String recipientEmail) {
        try {
            jakarta.mail.internet.MimeMessage mimeMessage = mailSender.createMimeMessage();
            org.springframework.mail.javamail.MimeMessageHelper helper = new org.springframework.mail.javamail.MimeMessageHelper(mimeMessage, "UTF-8");
            
            org.thymeleaf.context.Context context = new org.thymeleaf.context.Context();
            context.setVariable("title", notification.getTitle());
            context.setVariable("message", notification.getMessage());
            context.setVariable("actionUrl", notification.getActionUrl() != null ? "https://codesync.dev" + notification.getActionUrl() : null);
            
            String htmlContent = templateEngine.process("notification-email", context);

            helper.setFrom("noreply@codesync.dev");
            helper.setTo(recipientEmail);
            helper.setSubject("[CodeSync] " + notification.getTitle());
            helper.setText(htmlContent, true);
            
            mailSender.send(mimeMessage);
            log.info("Email sent to {} for notification id={}", recipientEmail, notification.getId());
            return true;
        } catch (Exception e) {
            log.error("Failed to send email for notification id={}: {}", notification.getId(), e.getMessage());
            return false;
        }
    }
}
