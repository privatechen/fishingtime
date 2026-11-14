package com.fishingtime.partygame.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fishingtime.partygame.room.RoomManager;
import com.fishingtime.partygame.room.RoomManager.Room;
import com.fishingtime.partygame.room.RoomManager.RematchResult;
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
    private final DrawGuessWebSocketRouter drawGuess;
    private final EitherOrWebSocketRouter eitherOr;

    public PartyGameWebSocketHandler(RoomManager rooms, ObjectMapper mapper, DrawGuessWebSocketRouter drawGuess, EitherOrWebSocketRouter eitherOr) {
        this.rooms = rooms;
        this.mapper = mapper;
        this.drawGuess = drawGuess;
        this.eitherOr = eitherOr;
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        if (message.getPayloadLength() > 16384) {
            send(session, "ERROR", Map.of("message", "消息过大"));
            return;
        }
        try {
            JsonNode body = mapper.readTree(message.getPayload());
            String type = requiredText(body, "type");
            if (!type.startsWith("DG_") && message.getPayloadLength() > 2048) {
                send(session, "ERROR", Map.of("message", "消息过大"));
                return;
            }
            if (eitherOr.supports(type)) {
                eitherOr.handle(session, body, type);
                return;
            }
            if (drawGuess.supports(type)) {
                drawGuess.handle(session, body, type);
                return;
            }
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
                case "REMATCH_REQUEST" -> {
                    RematchResult result = rooms.requestRematch(session);
                    if ("RESTARTED".equals(result.status())) {
                        broadcast(result.room(), "GAME_RESTARTED", rooms.snapshot(result.room()));
                    } else {
                        WebSocketSession opponent = rooms.sessions(result.room()).stream()
                                .filter(target -> !target.getId().equals(session.getId()))
                                .findFirst().orElse(null);
                        if (opponent != null) send(opponent, "REMATCH_INVITE", Map.of());
                        send(session, "REMATCH_PENDING", Map.of("expiresIn", 30));
                    }
                }
                case "REMATCH_ACCEPT" -> {
                    RematchResult result = rooms.respondRematch(session, true);
                    broadcast(result.room(), "GAME_RESTARTED", rooms.snapshot(result.room()));
                }
                case "REMATCH_REJECT" -> {
                    RematchResult result = rooms.respondRematch(session, false);
                    WebSocketSession inviter = rooms.sessions(result.room()).stream()
                            .filter(target -> !target.getId().equals(session.getId()))
                            .findFirst().orElse(null);
                    if (inviter != null) send(inviter, "REMATCH_REJECTED", Map.of());
                    send(session, "REMATCH_DECLINED", Map.of());
                }
                case "REMATCH_CANCEL" -> {
                    RematchResult result = rooms.cancelRematch(session);
                    WebSocketSession opponent = rooms.sessions(result.room()).stream()
                            .filter(target -> !target.getId().equals(session.getId()))
                            .findFirst().orElse(null);
                    if (opponent != null) send(opponent, "REMATCH_CANCELLED", Map.of());
                    send(session, "REMATCH_CANCELLED", Map.of());
                }
                case "LEAVE_ROOM" -> {
                    Room room = rooms.leave(session);
                    // Acknowledge the leaver first. A failure while notifying the
                    // remaining player must never prevent the leaver from exiting.
                    send(session, "LEFT_ROOM", Map.of());
                    if (room != null) {
                        try {
                            broadcast(room, "PLAYER_LEFT", rooms.snapshot(room));
                        } catch (Exception notifyError) {
                            log.warn("[PartyGame] Failed to notify remaining players after leave, roomId={}",
                                    room.code, notifyError);
                        }
                    }
                }
                case "PING" -> send(session, "PONG", Map.of());
                default -> send(session, "ERROR", Map.of("message", "未知消息类型"));
            }
        } catch (IllegalArgumentException ex) {
            send(session, "ERROR", Map.of("message", ex.getMessage() == null ? "请求无效" : ex.getMessage()));
        } catch (Exception ex) {
            log.error("[PartyGame] Message handling failed, sessionId={}", session.getId(), ex);
            send(session, "ERROR", Map.of("message", "服务器处理失败"));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        if (session.getAttributes().containsKey("eitherOrRoomId")) {
            eitherOr.disconnected(session);
            return;
        }
        if (session.getAttributes().containsKey("drawGuessRoomId")) {
            drawGuess.disconnected(session);
            return;
        }
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
