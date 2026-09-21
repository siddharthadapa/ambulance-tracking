package com.ats.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP over WebSocket configuration.
 *
 * Why WebSockets instead of REST polling for live tracking: a patient
 * screen polling GET /ambulance/{id}/location every 2s generates a full
 * HTTP request/response (headers, TCP overhead, auth re-check) per poll,
 * and location freshness is capped by the poll interval. A single
 * persistent WebSocket connection lets the driver push a location frame
 * the instant it changes, and the broker fans it out to every subscribed
 * client (patient app + dispatcher map) with sub-200ms latency and a
 * fraction of the bandwidth.
 *
 * Topics:
 *   /topic/ambulance.{id}.location  - broadcast of live GPS coordinates
 *   /topic/dispatch.{bookingId}     - dispatch status updates for a booking
 *
 * Client sends to:
 *   /app/location.update            - driver publishes its own position
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }
}
