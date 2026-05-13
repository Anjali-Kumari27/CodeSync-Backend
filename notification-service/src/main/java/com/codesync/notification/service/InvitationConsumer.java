package com.codesync.notification.service;

import com.codesync.notification.dto.InvitationMailEvent;
import com.codesync.notification.dto.CreateNotificationRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class InvitationConsumer {

    private final JavaMailSender mailSender;
    private final NotificationService notificationService;
   

    @RabbitListener(queues = "invite_queue")
    public void consumeInvite(InvitationMailEvent event) {
    	log.info("INVITE EVENT RECEIVED");
        try {
            SimpleMailMessage mail = new SimpleMailMessage();

            mail.setTo(event.getToEmail());
            mail.setSubject("CodeSync Project Invitation");

            mail.setText(
                    "Hello,\n\n" +
                    "You have been invited to collaborate on project: "
                    + event.getProjectName() + "\n\n" +
                    "Role: " + event.getRole() + "\n" +
                    "Invited By: " + event.getInvitedBy() + "\n\n" +
                    "Accept Invitation:\n" +
                    event.getInviteLink() + "\n\n" +
                    "Regards,\nCodeSync Team"
            );

            log.info("Creating notification for user {}", event.getRecipientId());
            CreateNotificationRequest req = CreateNotificationRequest.builder()
                    .recipientId(event.getRecipientId())
                    .type("COLLAB")
                    .title("Project Invitation")
                    .message(event.getInvitedBy() +
                            " invited you to collaborate on " +
                            event.getProjectName())
                    .projectId(event.getProjectId())
                    .build();

            notificationService.createNotification(
                    req,
                    event.getToEmail()
            );
            
            log.info("Notification saved");
            
            mailSender.send(mail);

            log.info("Invitation mail sent to {}", event.getToEmail());

        } catch (Exception e) {
        	e.printStackTrace();
        }
    }
}