package com.codesync.project.service;

import com.codesync.project.config.RabbitMQConfig;
import com.codesync.project.dto.InvitationMailEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class InvitationProducer {

    private final RabbitTemplate rabbitTemplate;

    public void sendInvitation(InvitationMailEvent event) {
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.INVITE_EXCHANGE,
                RabbitMQConfig.INVITE_ROUTING_KEY,
                event
        );
    }
}