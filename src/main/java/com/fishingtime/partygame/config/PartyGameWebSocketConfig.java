package com.fishingtime.partygame.config;

import com.fishingtime.partygame.websocket.PartyGameWebSocketHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class PartyGameWebSocketConfig implements WebSocketConfigurer {
    private static final Logger log = LoggerFactory.getLogger(PartyGameWebSocketConfig.class);
    private final PartyGameWebSocketHandler handler;

    public PartyGameWebSocketConfig(PartyGameWebSocketHandler handler) {
        this.handler = handler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/partygame").setAllowedOrigins("*");
        log.info("[PartyGame] WebSocket endpoint registered: /ws/partygame, handler={}", handler.getClass().getName());
    }
}
