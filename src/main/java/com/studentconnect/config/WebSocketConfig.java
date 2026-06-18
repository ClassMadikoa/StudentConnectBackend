package com.studentconnect.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.*;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // In-memory broker for topic subscriptions
        // For production: replace with RabbitMQ relay for multi-instance support
        registry.enableSimpleBroker("/topic", "/queue");

        // Prefix for @MessageMapping methods in controllers
        registry.setApplicationDestinationPrefixes("/app");

        // Prefix for user-specific messages (private DMs)
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // SockJS fallback for React Native / web clients
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS();

        // Native WebSocket for mobile clients that support it directly
        registry.addEndpoint("/ws-native")
                .setAllowedOriginPatterns("*");
    }
}
