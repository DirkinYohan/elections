package com.elections.infrastructure.websocket;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // WebSocket channels for poll workers (juries) and verification auditors
        // registry.addHandler(juriesWebSocketHandler, "/ws/juries").setAllowedOrigins("*");
        // registry.addHandler(verificationWebSocketHandler, "/ws/verification").setAllowedOrigins("*");
    }
}

