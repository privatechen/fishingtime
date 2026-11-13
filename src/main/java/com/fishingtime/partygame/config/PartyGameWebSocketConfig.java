package com.fishingtime.partygame.config;

import com.fishingtime.partygame.websocket.PartyGameWebSocketHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.server.HandshakeInterceptor;
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
        registry.addHandler(handler, "/ws/partygame")
                .addInterceptors(new HandshakeInterceptor() {
                    @Override
                    public boolean beforeHandshake(
                            org.springframework.http.server.ServerHttpRequest request,
                            org.springframework.http.server.ServerHttpResponse response,
                            org.springframework.web.socket.WebSocketHandler wsHandler,
                            java.util.Map<String, Object> attributes) {
                        log.info("[PartyGame] WebSocket handshake request: uri={}, method={}, upgrade={}, connection={}, origin={}, remote={}",
                                request.getURI(),
                                request.getMethod(),
                                request.getHeaders().getFirst("Upgrade"),
                                request.getHeaders().getFirst("Connection"),
                                request.getHeaders().getFirst("Origin"),
                                request.getRemoteAddress());
                        return true;
                    }

                    @Override
                    public void afterHandshake(
                            org.springframework.http.server.ServerHttpRequest request,
                            org.springframework.http.server.ServerHttpResponse response,
                            org.springframework.web.socket.WebSocketHandler wsHandler,
                            Exception exception) {
                        if (exception != null) {
                            log.warn("[PartyGame] WebSocket handshake failed: uri={}, reason={}",
                                    request.getURI(), exception.toString());
                        } else {
                            log.info("[PartyGame] WebSocket handshake completed: uri={}", request.getURI());
                        }
                    }
                })
                .setAllowedOrigins("*");
        log.info("[PartyGame] WebSocket endpoint registered: /ws/partygame, handler={}", handler.getClass().getName());
    }
}
