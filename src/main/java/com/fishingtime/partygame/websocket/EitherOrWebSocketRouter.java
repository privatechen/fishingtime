package com.fishingtime.partygame.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fishingtime.partygame.eitheror.EitherOrGameService;
import com.fishingtime.partygame.eitheror.EitherOrManager;
import com.fishingtime.partygame.eitheror.EitherOrManager.Room;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class EitherOrWebSocketRouter {
    private static final Logger log = LoggerFactory.getLogger(EitherOrWebSocketRouter.class);
    private final EitherOrManager manager;
    private final EitherOrGameService game;
    private final ObjectMapper mapper;

    public EitherOrWebSocketRouter(EitherOrManager manager, EitherOrGameService game, ObjectMapper mapper) {
        this.manager = manager;
        this.game = game;
        this.mapper = mapper;
    }

    public boolean supports(String type) { return type != null && type.startsWith("EO_"); }

    public void handle(WebSocketSession session, JsonNode body, String type) throws Exception {
        try {
            switch (type) {
                case "EO_CREATE_ROOM" -> {
                    Room room = manager.create(session, text(body, "nickname", "玩家"));
                    EitherOrManager.Player player = manager.playerForSession(session);
                    send(session, "EO_ROOM_CREATED", Map.of("room", manager.snapshot(room),
                            "playerId", player.id, "reconnectToken", player.reconnectToken));
                }
                case "EO_JOIN_ROOM" -> {
                    Room room = manager.join(session, required(body, "roomId"), text(body, "nickname", "玩家"));
                    EitherOrManager.Player player = manager.playerForSession(session);
                    broadcast(room, "EO_ROOM_UPDATED", manager.snapshot(room));
                    send(session, "EO_ROOM_JOINED", Map.of("room", manager.snapshot(room),
                            "playerId", player.id, "reconnectToken", player.reconnectToken));
                }
                case "EO_RECONNECT" -> {
                    EitherOrManager.Player player = manager.reconnect(session, required(body, "roomId"),
                            required(body, "playerId"), required(body, "reconnectToken"));
                    Room room = manager.roomFor(session);
                    send(session, "EO_RECONNECTED", Map.of("room", manager.snapshot(room),
                            "playerId", player.id, "reconnectToken", player.reconnectToken));
                    broadcastExcept(room, session, "EO_ROOM_UPDATED", manager.snapshot(room));
                }
                case "EO_START_GAME" -> {
                    Room room = manager.roomFor(session);
                    var started = game.start(room.roomId, manager.playerId(session), System.currentTimeMillis());
                    broadcast(room, "EO_QUESTION_STARTED", questionPayload(started));
                }
                case "EO_SUBMIT_VOTE" -> {
                    Room room = manager.roomFor(session);
                    var submission = game.submit(room.roomId, manager.playerId(session),
                            text(body, "choice", ""), System.currentTimeMillis());
                    if (submission.result() != null) {
                        broadcast(room, "EO_ROUND_RESULT", resultPayload(submission.result()));
                    } else {
                        broadcast(room, "EO_VOTE_PROGRESS", submission.progress());
                    }
                }
                case "EO_NEXT_QUESTION" -> {
                    Room room = manager.roomFor(session);
                    var started = game.next(room.roomId, manager.playerId(session), System.currentTimeMillis());
                    if (started == null) {
                        broadcast(room, "EO_GAME_OVER", Map.of("scores", gameScores(room),
                                "room", manager.snapshot(room)));
                    } else {
                        broadcast(room, "EO_QUESTION_STARTED", questionPayload(started));
                    }
                }
                case "EO_LEAVE_ROOM" -> {
                    Room room = manager.leave(session);
                    send(session, "EO_LEFT_ROOM", Map.of());
                    if (room != null && !manager.sessions(room).isEmpty()) {
                        broadcast(room, "EO_ROOM_UPDATED", manager.snapshot(room));
                    }
                }
                default -> throw new IllegalArgumentException("未知的二选一消息类型");
            }
        } catch (IllegalArgumentException ex) {
            send(session, "EO_ERROR", Map.of("message",
                    ex.getMessage() == null ? "请求无效" : ex.getMessage()));
        } catch (Exception ex) {
            log.error("[EitherOr] message failed, session={}", session.getId(), ex);
            send(session, "EO_ERROR", Map.of("message", "服务器处理失败"));
        }
    }

    public void disconnected(WebSocketSession session) {
        Room room = manager.disconnect(session);
        if (room == null) return;
        try { broadcast(room, "EO_ROOM_UPDATED", manager.snapshot(room)); }
        catch (Exception ex) { log.warn("[EitherOr] disconnect broadcast failed", ex); }
    }

    @Scheduled(fixedDelay = 250)
    public void tick() {
        try {
            for (var result : game.processTimers(System.currentTimeMillis())) {
                Room room = manager.getRoomFromSnapshot(result.room());
                if (room != null) broadcast(room, "EO_ROUND_RESULT", resultPayload(result));
            }
            manager.cleanup();
        } catch (Exception ex) {
            log.warn("[EitherOr] timer tick failed", ex);
        }
    }

    private Map<String,Object> questionPayload(EitherOrGameService.QuestionStarted started) {
        Map<String,Object> data = new LinkedHashMap<>();
        data.put("round", started.round());
        data.put("totalRounds", started.totalRounds());
        data.put("deadline", started.deadline());
        data.put("question", Map.of("id", started.question().id(), "category", started.question().category(),
                "title", started.question().title(), "optionA", started.question().optionA(),
                "optionB", started.question().optionB()));
        data.put("room", started.room());
        return data;
    }

    private Map<String,Object> resultPayload(EitherOrGameService.RoundResult result) {
        Map<String,Object> data = new LinkedHashMap<>();
        data.put("round", result.round()); data.put("votesA", result.votesA());
        data.put("votesB", result.votesB()); data.put("minority", result.minority());
        data.put("scores", result.scores()); data.put("room", result.room());
        data.put("nextState", "RESULT");
        return data;
    }

    private java.util.List<Map<String,Object>> gameScores(Room room) {
        synchronized (room) {
            return room.players.values().stream()
                    .sorted(java.util.Comparator.comparingInt((EitherOrManager.Player p) -> p.score).reversed()
                            .thenComparing(p -> p.nickname))
                    .map(p -> {
                        Map<String,Object> v = new LinkedHashMap<>();
                        v.put("playerId", p.id); v.put("nickname", p.nickname); v.put("score", p.score);
                        v.put("online", p.online); return v;
                    }).toList();
        }
    }

    private String required(JsonNode body, String field) {
        String value = text(body, field, null);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("缺少参数：" + field);
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
        for (WebSocketSession target : manager.sessions(room))
            if (!target.getId().equals(excluded.getId())) send(target, type, data);
    }
    private void send(WebSocketSession session, String type, Object data) throws Exception {
        if (session == null || !session.isOpen()) return;
        Map<String,Object> envelope = new LinkedHashMap<>();
        envelope.put("type", type); envelope.put("data", data);
        synchronized (session) { session.sendMessage(new TextMessage(mapper.writeValueAsString(envelope))); }
    }
}
