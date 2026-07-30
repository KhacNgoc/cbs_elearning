package com.bidv.coretraining.config;

import com.bidv.coretraining.websocket.NotificationWebSocketHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.socket.config.annotation.*;

import java.util.List;

@Configuration
@EnableWebSocket
@EnableScheduling
public class WebSocketConfig implements WebSocketConfigurer {
    private final NotificationWebSocketHandler handler;
    private final List<String> allowedOrigins;

    public WebSocketConfig(NotificationWebSocketHandler handler,
                           @Value("${app.cors.allowed-origins}") List<String> allowedOrigins) {
        this.handler = handler;
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/notifications")
                .setAllowedOrigins(allowedOrigins.toArray(String[]::new));
    }
}
