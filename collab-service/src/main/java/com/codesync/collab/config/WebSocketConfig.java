package com.codesync.collab.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSocket + STOMP configuration for real-time collaborative editing.
 *
 * Connection flow:
 *  1. Client connects to: ws://localhost:8084/ws/collab
 *     (SockJS fallback: http://localhost:8084/ws/collab)
 *  2. Client subscribes to a session topic: /topic/session/{sessionId}
 *  3. Client sends edit messages to: /app/collab/edit
 *  4. Client sends join/leave to:    /app/collab/presence
 *
 * The in-memory broker is used for development.
 * For production, replace with Redis Pub/Sub relay
 * for distributed multi-instance collaboration.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    /**
     * Configure broker channels.
     *
     * /topic -> session broadcasts
     * /queue -> user specific events
     * /user  -> private messaging
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {

        ThreadPoolTaskScheduler scheduler =
                new ThreadPoolTaskScheduler();

        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("ws-heartbeat-");
        scheduler.initialize();

        registry.enableSimpleBroker("/topic", "/queue")
                .setHeartbeatValue(new long[]{10000, 10000})
                .setTaskScheduler(scheduler);

        registry.setApplicationDestinationPrefixes("/app");

        registry.setUserDestinationPrefix("/user");
    }

    /**
     * Register websocket endpoints.
     *
     * SockJS fallback is enabled for browser compatibility.
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {

        registry.addEndpoint("/ws/collab")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }
}