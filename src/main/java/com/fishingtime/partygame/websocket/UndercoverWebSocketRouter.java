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

import java.util.LinkedHashMap;
import java.util.List;
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
                    if (allReady) {
                        broadcast(room, "UC_SPEAKING_STARTED", manager.snapshot(room));
                    } else {
                        broadcast(room, "UC_PLAYER_READY", manager.snapshot(room));
                    }
                }
                case "UC_SPEAKER_DONE" -> {
                    manager.speakerDone(session);
                    Room room = manager.roomFor(session);
                    broadcast(room, "UC_SPEAKER_CHANGED", manager.snapshot(room));
                }
                case "UC_VOTE" -> {
                    String targetPlayerId = required(body, "targetPlayerId");
                    Room room = manager.roomFor(session);
                    Map<String,Object> vote = manager.submitVote(session, targetPlayerId);
                    if (Boolean.TRUE.equals(vote.get("allVoted"))) {
                        broadcast(room, "UC_VOTE_RESULT", vote);
                    } else {
                        send(session, "UC_VOTE_ACCEPTED", Map.of(
                                "votedCount", vote.get("votedCount"),
                                "eligibleCount", vote.get("eligibleCount")));
                        broadcast(room, "UC_VOTE_PROGRESS", vote);
                    }
                }
                case "UC_NEXT_ROUND" -> {
                    manager.startNextRound(session);
                    Room room = manager.roomFor(session);
                    broadcast(room, "UC_SPEAKING_STARTED", manager.snapshot(room));
                }
                case "UC_RECONNECT" -> {
                    Player player = manager.reconnect(session, required(body, "roomId"),
                            required(body, "playerId"), required(body, "reconnectToken"));
                    Room room = manager.roomFor(session);
                    send(session, "UC_RECONNECTED", Map.of(
                            "room", manager.snapshot(room),
                            "playerId", player.id,
                            "reconnectToken", player.reconnectToken,
                            "role", player.role == null ? "" : player.role,
                            "word", player.word == null ? "" : player.word,
                            "ready", player.ready,
                            "hasVoted", manager.hasVoted(room, player.id)));
                    if (manager.lastVoteResult(room) != null) {
                        send(session, "UC_VOTE_RESULT", manager.lastVoteResult(room));
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

    @Scheduled(fixedDelay = 1000)
    public void advanceTimedPhases() {
        long now = System.currentTimeMillis();
        for (Room room : manager.allRooms()) {
            try {
                if (manager.advanceSpeakerIfExpired(room, now)) {
                    broadcast(room, "UC_SPEAKER_CHANGED", manager.snapshot(room));
                }
                Map<String,Object> voteResult = manager.resolveVoteIfExpired(room, now);
                if (voteResult != null) {
                    broadcast(room, "UC_VOTE_RESULT", voteResult);
                }
            } catch (Exception ex) {
                log.warn("[Undercover] timed phase update failed, room={}", room.roomId, ex);
            }
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
