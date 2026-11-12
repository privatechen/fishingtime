package com.fishingtime.partygame.config;

import com.fishingtime.partygame.websocket.PartyGameWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class PartyGameWebSocketConfig implements WebSocketConfigurer {
    private final PartyGameWebSocketHandler handler;

    public PartyGameWebSocketConfig(PartyGameWebSocketHandler handler) {
        this.handler = handler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/partygame").setAllowedOrigins("*");
    }
}
