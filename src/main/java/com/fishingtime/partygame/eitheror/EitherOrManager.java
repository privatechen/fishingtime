package com.fishingtime.partygame.eitheror;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class EitherOrManager {
    public static final int MIN_PLAYERS = 2;
    public static final int MAX_PLAYERS = 20;
    private static final long ROOM_TTL_MILLIS = 30 * 60 * 1000L;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String ATTR_ROOM = "eitherOrRoomId";
    private static final String ATTR_PLAYER = "eitherOrPlayerId";
    private final Map<String, Room> rooms = new ConcurrentHashMap<>();

    public synchronized Room create(WebSocketSession session, String nickname) {
        requireUnbound(session);
        String code;
        do { code = String.format("%04d", RANDOM.nextInt(10000)); } while (rooms.containsKey(code));
        Room room = new Room(code);
        Player host = newPlayer(session, nickname);
        room.players.put(host.id, host);
        room.hostPlayerId = host.id;
        rooms.put(code, room);
        bind(session, code, host.id);
        room.touch();
        return room;
    }

    public synchronized Room join(WebSocketSession session, String roomId, String nickname) {
        requireUnbound(session);
        Room room = get(roomId);
        synchronized (room) {
            if (room.state != State.WAITING) throw new IllegalArgumentException("游戏已经开始，无法加入");
            if (room.players.size() >= MAX_PLAYERS) throw new IllegalArgumentException("房间已满");
            Player player = newPlayer(session, nickname);
            room.players.put(player.id, player);
            bind(session, roomId, player.id);
            room.touch();
            return room;
        }
    }

    public Room get(String roomId) {
        if (roomId == null || !roomId.matches("\\d{4}")) throw new IllegalArgumentException("请输入正确的四位房间号");
        Room room = rooms.get(roomId);
        if (room == null || System.currentTimeMillis() - room.updatedAt > ROOM_TTL_MILLIS)
            throw new IllegalArgumentException("房间不存在或已过期");
        return room;
    }

    public Room roomFor(WebSocketSession session) {
        String roomId = attr(session, ATTR_ROOM);
        String playerId = attr(session, ATTR_PLAYER);
        if (roomId == null || playerId == null) throw new IllegalArgumentException("请先加入二选一房间");
        Room room = get(roomId);
        synchronized (room) {
            if (!room.players.containsKey(playerId)) throw new IllegalArgumentException("玩家身份无效");
        }
        return room;
    }

    public Player playerForSession(WebSocketSession session) {
        String roomId = attr(session, ATTR_ROOM), playerId = attr(session, ATTR_PLAYER);
        if (roomId == null || playerId == null) return null;
        Room room = rooms.get(roomId);
        if (room == null) return null;
        synchronized (room) { return room.players.get(playerId); }
    }

    public Room getRoomFromSnapshot(Map<String,Object> snapshot) {
        Object id = snapshot == null ? null : snapshot.get("roomId");
        return id instanceof String ? rooms.get((String) id) : null;
    }

    public String playerId(WebSocketSession session) {
        String id = attr(session, ATTR_PLAYER);
        if (id == null) throw new IllegalArgumentException("玩家身份无效");
        return id;
    }

    public Player reconnect(WebSocketSession session, String roomId, String playerId, String token) {
        requireUnbound(session);
        Room room = get(roomId);
        synchronized (room) {
            Player p = room.players.get(playerId);
            if (p == null || token == null || !constantTimeEquals(p.reconnectToken, token))
                throw new IllegalArgumentException("重连凭证无效");
            if (p.online) throw new IllegalArgumentException("该玩家已有有效连接");
            p.session = session;
            p.online = true;
            bind(session, roomId, playerId);
            room.touch();
            return p;
        }
    }

    public Room leave(WebSocketSession session) {
        String roomId = attr(session, ATTR_ROOM), playerId = attr(session, ATTR_PLAYER);
        clearBinding(session);
        if (roomId == null || playerId == null) return null;
        Room room = rooms.get(roomId);
        if (room == null) return null;
        synchronized (room) {
            room.players.remove(playerId);
            if (room.players.isEmpty()) {
                rooms.remove(roomId, room);
                return room;
            }
            if (playerId.equals(room.hostPlayerId)) electHost(room);
            room.touch();
            return room;
        }
    }

    public Room disconnect(WebSocketSession session) {
        String roomId = attr(session, ATTR_ROOM), playerId = attr(session, ATTR_PLAYER);
        clearBinding(session);
        if (roomId == null || playerId == null) return null;
        Room room = rooms.get(roomId);
        if (room == null) return null;
        synchronized (room) {
            Player p = room.players.get(playerId);
            if (p == null || p.session == null || !p.session.getId().equals(session.getId())) return null;
            p.online = false;
            p.session = null;
            if (playerId.equals(room.hostPlayerId)) electHost(room);
            room.touch();
            return room;
        }
    }

    public Collection<Room> allRooms() { return List.copyOf(rooms.values()); }

    public List<WebSocketSession> sessions(Room room) {
        synchronized (room) {
            return room.players.values().stream().filter(p -> p.online && p.session != null)
                    .map(p -> p.session).toList();
        }
    }

    public Map<String, Object> snapshot(Room room) {
        synchronized (room) {
            List<Map<String,Object>> players = room.players.values().stream().map(p -> {
                Map<String,Object> v = new LinkedHashMap<>();
                v.put("playerId", p.id); v.put("nickname", p.nickname); v.put("online", p.online);
                v.put("score", p.score); v.put("hasVoted", p.choice != null);
                return v;
            }).toList();
            Map<String,Object> v = new LinkedHashMap<>();
            v.put("roomId", room.roomId); v.put("gameType", "either-or");
            v.put("hostPlayerId", room.hostPlayerId); v.put("state", room.state.name());
            v.put("round", room.round); v.put("totalRounds", room.totalRounds);
            v.put("players", players); v.put("maxPlayers", MAX_PLAYERS);
            v.put("deadline", room.deadline);
            if (room.currentQuestion != null) {
                v.put("question", Map.of("id", room.currentQuestion.id(), "category", room.currentQuestion.category(),
                        "title", room.currentQuestion.title(), "optionA", room.currentQuestion.optionA(),
                        "optionB", room.currentQuestion.optionB()));
            }
            if (room.lastResult != null) v.put("lastResult", room.lastResult);
            return v;
        }
    }

    public void cleanup() {
        long now = System.currentTimeMillis();
        rooms.entrySet().removeIf(e -> now - e.getValue().updatedAt > ROOM_TTL_MILLIS);
    }

    private void electHost(Room room) {
        room.hostPlayerId = room.players.values().stream().filter(p -> p.online)
                .map(p -> p.id).findFirst().orElseGet(() ->
                        room.players.keySet().stream().findFirst().orElse(null));
    }

    private void requireUnbound(WebSocketSession session) {
        if (session == null || !session.isOpen()) throw new IllegalArgumentException("连接已失效");
        if (session.getAttributes().containsKey("roomId")
                || session.getAttributes().containsKey("drawGuessRoomId")
                || session.getAttributes().containsKey(ATTR_ROOM))
            throw new IllegalArgumentException("当前连接已经加入其他房间");
    }

    private Player newPlayer(WebSocketSession session, String nickname) {
        String name = nickname == null || nickname.isBlank() ? "玩家" : nickname.trim();
        if (name.length() > 12) name = name.substring(0, 12);
        return new Player(UUID.randomUUID().toString(), name, UUID.randomUUID().toString(), session);
    }

    private void bind(WebSocketSession session, String roomId, String playerId) {
        session.getAttributes().put(ATTR_ROOM, roomId);
        session.getAttributes().put(ATTR_PLAYER, playerId);
    }
    private void clearBinding(WebSocketSession session) {
        session.getAttributes().remove(ATTR_ROOM);
        session.getAttributes().remove(ATTR_PLAYER);
    }
    private String attr(WebSocketSession session, String key) {
        Object v = session.getAttributes().get(key);
        return v instanceof String ? (String) v : null;
    }
    private boolean constantTimeEquals(String a, String b) {
        return a != null && b != null && java.security.MessageDigest.isEqual(
                a.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                b.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    public enum State { WAITING, VOTING, RESULT, GAME_OVER }
    public static class Room {
        public final String roomId;
        public final Map<String,Player> players = new LinkedHashMap<>();
        public State state = State.WAITING;
        public String hostPlayerId;
        public int round;
        public final int totalRounds = 10;
        public List<Question> questions = new ArrayList<>();
        public Question currentQuestion;
        public long deadline;
        public long updatedAt = System.currentTimeMillis();
        public long resultAt;
        public Map<String,Object> lastResult;
        public Room(String roomId) { this.roomId = roomId; }
        public void touch() { updatedAt = System.currentTimeMillis(); }
    }
    public static class Player {
        public final String id, nickname, reconnectToken;
        public WebSocketSession session;
        public boolean online = true;
        public int score;
        public String choice;
        public Player(String id, String nickname, String token, WebSocketSession session) {
            this.id=id; this.nickname=nickname; this.reconnectToken=token; this.session=session;
        }
    }
    public record Question(int id, String category, String title, String optionA, String optionB) {}
}
