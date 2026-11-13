package com.fishingtime.partygame.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fishingtime.partygame.room.RoomManager;
import com.fishingtime.partygame.room.RoomManager.Room;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class PartyGameWebSocketHandler extends TextWebSocketHandler {
    private static final Logger log = LoggerFactory.getLogger(PartyGameWebSocketHandler.class);
    private final RoomManager rooms;
    private final ObjectMapper mapper;

    public PartyGameWebSocketHandler(RoomManager rooms, ObjectMapper mapper) {
        this.rooms = rooms;
        this.mapper = mapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.info("[PartyGame] WebSocket connected: sessionId={}, uri={}", session.getId(), session.getUri());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        if (message.getPayloadLength() > 2048) {
            send(session, "ERROR", Map.of("message", "消息过大"));
            return;
        }
        try {
            JsonNode body = mapper.readTree(message.getPayload());
            String type = requiredText(body, "type");
            log.debug("[PartyGame] WebSocket message: sessionId={}, type={}", session.getId(), type);
            switch (type) {
                case "CREATE_ROOM" -> {
                    Room room = rooms.create(session, optionalText(body, "nickname"));
                    send(session, "ROOM_CREATED", rooms.snapshot(room));
                }
                case "JOIN_ROOM" -> {
                    Room room = rooms.join(session, requiredText(body, "roomId"), optionalText(body, "nickname"));
                    broadcast(room, "ROOM_UPDATED", rooms.snapshot(room));
                    broadcast(room, "GAME_START", rooms.snapshot(room));
                }
                case "MAKE_MOVE" -> {
                    int x = body.path("x").asInt(-1), y = body.path("y").asInt(-1);
                    RoomManager.MoveResult result = rooms.move(session, x, y);
                    broadcast(result.room(), "MOVE_UPDATED", rooms.snapshot(result.room()));
                }
                case "RESTART" -> {
                    Room room = rooms.restart(session);
                    broadcast(room, "GAME_RESTARTED", rooms.snapshot(room));
                }
                case "LEAVE_ROOM" -> {
                    Room room = rooms.leave(session);
                    if (room != null) broadcast(room, "PLAYER_LEFT", rooms.snapshot(room));
                    send(session, "LEFT_ROOM", Map.of());
                }
                case "PING" -> send(session, "PONG", Map.of());
                default -> send(session, "ERROR", Map.of("message", "未知消息类型"));
            }
        } catch (IllegalArgumentException ex) {
            log.warn("[PartyGame] Invalid WebSocket request: sessionId={}, reason={}", session.getId(), ex.getMessage());
            send(session, "ERROR", Map.of("message", ex.getMessage() == null ? "请求无效" : ex.getMessage()));
        } catch (Exception ex) {
            log.error("[PartyGame] WebSocket message handling failed: sessionId={}", session.getId(), ex);
            send(session, "ERROR", Map.of("message", "服务器处理失败"));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        log.info("[PartyGame] WebSocket disconnected: sessionId={}, code={}, reason={}", session.getId(), status.getCode(), status.getReason());
        rooms.markDisconnected(session);
        Room room = rooms.leave(session);
        if (room != null) {
            try { broadcast(room, "PLAYER_LEFT", rooms.snapshot(room)); }
            catch (Exception ignored) { }
        }
    }

    private String requiredText(JsonNode body, String field) {
        String value = optionalText(body, field);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("缺少参数：" + field);
        return value;
    }

    private String optionalText(JsonNode body, String field) {
        JsonNode node = body.get(field);
        return node == null || node.isNull() ? null : node.asText();
    }

    private void broadcast(Room room, String type, Object data) throws Exception {
        for (WebSocketSession target : rooms.sessions(room)) send(target, type, data);
    }

    private void send(WebSocketSession session, String type, Object data) throws Exception {
        if (!session.isOpen()) return;
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("type", type);
        envelope.put("data", data);
        synchronized (session) {
            session.sendMessage(new TextMessage(mapper.writeValueAsString(envelope)));
        }
    }
}
