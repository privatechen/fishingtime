package com.fishingtime.partygame.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fishingtime.partygame.undercover.UndercoverManager;
import com.fishingtime.partygame.undercover.UndercoverManager.Player;
import com.fishingtime.partygame.undercover.UndercoverManager.Room;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.LinkedHashMap;\nimport java.util.List;
import java.util.Map;

@Component
public class UndercoverWebSocketRouter {
    private static final Logger log = LoggerFactory.getLogger(UndercoverWebSocketRouter.class);

    private final UndercoverManager manager;
    private final ObjectMapper mapper;

    public UndercoverWebSocketRouter(UndercoverManager manager, ObjectMapper mapper) {
        this.manager = manager;
        this.mapper = mapper;
    }

    public boolean supports(String type) {
        return type != null && type.startsWith("UC_");
    }

    public void handle(WebSocketSession session, JsonNode body, String type) throws Exception {
        try {
            switch (type) {
                case "UC_CREATE_ROOM" -> {
                    Room room = manager.create(session, text(body, "nickname", "玩家"));
                    Player player = manager.playerForSession(session);
                    send(session, "UC_ROOM_CREATED", Map.of(
                            "room", manager.snapshot(room),
                            "playerId", player.id,
                            "reconnectToken", player.reconnectToken));
                }
                case "UC_JOIN_ROOM" -> {
                    Room room = manager.join(session, required(body, "roomId"), text(body, "nickname", "玩家"));
                    Player player = manager.playerForSession(session);
                    broadcast(room, "UC_ROOM_UPDATED", manager.snapshot(room));
                    send(session, "UC_ROOM_JOINED", Map.of(
                            "room", manager.snapshot(room),
                            "playerId", player.id,
                            "reconnectToken", player.reconnectToken));
                }
                case "UC_START_GAME" -> {
                    List<Player> players = manager.startGame(session);
                    Room room = manager.roomFor(session);
                    for (Player target : players) {
                        if (target.session != null && target.online) {
                            send(target.session, "UC_GAME_STARTED", Map.of(
                                    "playerId", target.id,
                                    "role", target.role,
                                    "word", target.word,
                                    "round", room.round));
                        }
                    }
                    broadcast(room, "UC_ROOM_UPDATED", manager.snapshot(room));
                }
                case "UC_READY" -> {
                    boolean allReady = manager.markReady(session);
                    Room room = manager.roomFor(session);
                    broadcast(room, "UC_PLAYER_READY", manager.snapshot(room));
                    if (allReady) {
                        broadcast(room, "UC_ALL_READY", Map.of(
                                "message", "所有玩家已准备，下一阶段进入描述环节",
                                "round", room.round));
                    }
                }
                case "UC_RECONNECT" -> {
                    Player player = manager.reconnect(session, required(body, "roomId"),
                            required(body, "playerId"), required(body, "reconnectToken"));
                    Room room = manager.roomFor(session);
                    send(session, "UC_RECONNECTED", Map.of(
                            "room", manager.snapshot(room),
                            "playerId", player.id,
                            "reconnectToken", player.reconnectToken));
                    if (room.state == com.fishingtime.partygame.undercover.UndercoverState.ROLE_REVEAL
                            && player.role != null && player.word != null) {
                        send(session, "UC_GAME_STARTED", Map.of(
                                "playerId", player.id,
                                "role", player.role,
                                "word", player.word,
                                "round", room.round,
                                "ready", player.ready));
                    }
                    broadcastExcept(room, session, "UC_ROOM_UPDATED", manager.snapshot(room));
                }
                case "UC_LEAVE_ROOM" -> {
                    Room room = manager.leave(session);
                    send(session, "UC_LEFT_ROOM", Map.of());
                    if (room != null && !manager.sessions(room).isEmpty()) {
                        broadcast(room, "UC_ROOM_UPDATED", manager.snapshot(room));
                    }
                }
                default -> throw new IllegalArgumentException("未知的谁是卧底消息类型");
            }
        } catch (IllegalArgumentException ex) {
            send(session, "UC_ERROR", Map.of("message",
                    ex.getMessage() == null ? "请求无效" : ex.getMessage()));
        } catch (Exception ex) {
            log.error("[Undercover] message failed, session={}", session.getId(), ex);
            send(session, "UC_ERROR", Map.of("message", "服务器处理失败"));
        }
    }

    public void disconnected(WebSocketSession session) {
        Object playerId = session.getAttributes().get("undercoverPlayerId");
        Room room = manager.disconnect(session);
        if (room == null) return;
        try {
            broadcast(room, "UC_PLAYER_DISCONNECTED", Map.of(
                    "playerId", playerId,
                    "room", manager.snapshot(room)));
        } catch (Exception ex) {
            log.warn("[Undercover] disconnect broadcast failed", ex);
        }
    }

    @Scheduled(fixedDelay = 30000)
    public void cleanup() {
        manager.cleanup();
    }

    private String required(JsonNode body, String field) {
        String value = text(body, field, null);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("缺少参数：" + field);
        }
        return value;
    }

    private String text(JsonNode body, String field, String fallback) {
        JsonNode value = body.get(field);
        return value == null || value.isNull() ? fallback : value.asText();
    }

    private void broadcast(Room room, String type, Object data) throws Exception {
        for (WebSocketSession target : manager.sessions(room)) send(target, type, data);
    }

    private void broadcastExcept(Room room, WebSocketSession excluded, String type, Object data) throws Exception {
        for (WebSocketSession target : manager.sessions(room)) {
            if (!target.getId().equals(excluded.getId())) send(target, type, data);
        }
    }

    private void send(WebSocketSession session, String type, Object data) throws Exception {
        if (session == null || !session.isOpen()) return;
        Map<String,Object> envelope = new LinkedHashMap<>();
        envelope.put("type", type);
        envelope.put("data", data);
        synchronized (session) {
            session.sendMessage(new TextMessage(mapper.writeValueAsString(envelope)));
        }
    }
}
