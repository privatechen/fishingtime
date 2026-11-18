package com.fishingtime.partygame.undercover;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class UndercoverManager {
    public static final int MIN_PLAYERS = 4;
    public static final int MAX_PLAYERS = 12;
    private static final long ROOM_TTL_MILLIS = 30 * 60 * 1000L;
    private static final SecureRandom RANDOM = new SecureRandom();

    private static final String ATTR_ROOM = "undercoverRoomId";
    private static final String ATTR_PLAYER = "undercoverPlayerId";

    private final Map<String, Room> rooms = new ConcurrentHashMap<>();

    public synchronized Room create(WebSocketSession session, String nickname) {
        requireUnbound(session);
        String roomId;
        do { roomId = String.format("%04d", RANDOM.nextInt(10000)); }
        while (rooms.containsKey(roomId));

        Room room = new Room(roomId);
        Player host = newPlayer(session, nickname);
        room.players.put(host.id, host);
        room.hostPlayerId = host.id;
        rooms.put(roomId, room);
        bind(session, roomId, host.id);
        room.touch();
        return room;
    }

    public synchronized Room join(WebSocketSession session, String roomId, String nickname) {
        requireUnbound(session);
        Room room = get(roomId);
        synchronized (room) {
            if (room.state != UndercoverState.WAITING) {
                throw new IllegalArgumentException("游戏已经开始，无法加入");
            }
            if (room.players.size() >= MAX_PLAYERS) {
                throw new IllegalArgumentException("房间已满");
            }
            Player player = newPlayer(session, nickname);
            room.players.put(player.id, player);
            bind(session, roomId, player.id);
            room.touch();
            return room;
        }
    }

    public Room get(String roomId) {
        if (roomId == null || !roomId.matches("\\d{4}")) {
            throw new IllegalArgumentException("请输入正确的四位房间号");
        }
        Room room = rooms.get(roomId);
        if (room == null || expired(room)) {
            throw new IllegalArgumentException("房间不存在或已过期");
        }
        return room;
    }

    public Room roomFor(WebSocketSession session) {
        String roomId = attr(session, ATTR_ROOM);
        String playerId = attr(session, ATTR_PLAYER);
        if (roomId == null || playerId == null) {
            throw new IllegalArgumentException("请先加入谁是卧底房间");
        }
        Room room = get(roomId);
        synchronized (room) {
            if (!room.players.containsKey(playerId)) {
                throw new IllegalArgumentException("玩家身份无效");
            }
        }
        return room;
    }

    public String playerId(WebSocketSession session) {
        String id = attr(session, ATTR_PLAYER);
        if (id == null) throw new IllegalArgumentException("玩家身份无效");
        return id;
    }

    public Player playerForSession(WebSocketSession session) {
        String roomId = attr(session, ATTR_ROOM);
        String playerId = attr(session, ATTR_PLAYER);
        if (roomId == null || playerId == null) return null;
        Room room = rooms.get(roomId);
        if (room == null) return null;
        synchronized (room) {
            Player player = room.players.get(playerId);
            return player != null && player.session != null
                    && player.session.getId().equals(session.getId()) ? player : null;
        }
    }

    public synchronized Player reconnect(WebSocketSession session, String roomId, String playerId, String token) {
        requireUnbound(session);
        Room room = get(roomId);
        synchronized (room) {
            Player player = room.players.get(playerId);
            if (player == null || token == null || !constantTimeEquals(player.reconnectToken, token)) {
                throw new IllegalArgumentException("重连凭证无效");
            }
            if (player.online) throw new IllegalArgumentException("该玩家已有有效连接");
            player.session = session;
            player.online = true;
            bind(session, roomId, playerId);
            room.touch();
            return player;
        }
    }

    public synchronized Room disconnect(WebSocketSession session) {
        String roomId = attr(session, ATTR_ROOM);
        String playerId = attr(session, ATTR_PLAYER);
        clearBinding(session);
        if (roomId == null || playerId == null) return null;
        Room room = rooms.get(roomId);
        if (room == null) return null;

        synchronized (room) {
            Player player = room.players.get(playerId);
            if (player == null || player.session == null
                    || !player.session.getId().equals(session.getId())) return null;
            player.online = false;
            player.session = null;
            if (playerId.equals(room.hostPlayerId)) {
                room.hostPlayerId = room.players.values().stream()
                        .filter(p -> p.online)
                        .map(p -> p.id)
                        .findFirst()
                        .orElse(playerId);
            }
            room.touch();
            return room;
        }
    }

    public synchronized Room leave(WebSocketSession session) {
        String roomId = attr(session, ATTR_ROOM);
        String playerId = attr(session, ATTR_PLAYER);
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
            if (playerId.equals(room.hostPlayerId)) {
                room.hostPlayerId = room.players.values().stream()
                        .filter(p -> p.online)
                        .map(p -> p.id)
                        .findFirst()
                        .orElse(room.players.keySet().iterator().next());
            }
            room.touch();
            return room;
        }
    }

    public List<WebSocketSession> sessions(Room room) {
        synchronized (room) {
            return room.players.values().stream()
                    .filter(p -> p.online && p.session != null && p.session.isOpen())
                    .map(p -> p.session)
                    .toList();
        }
    }

    public Collection<Room> allRooms() {
        return List.copyOf(rooms.values());
    }

    public Map<String, Object> snapshot(Room room) {
        synchronized (room) {
            List<Map<String,Object>> players = room.players.values().stream().map(p -> {
                Map<String,Object> item = new LinkedHashMap<>();
                item.put("playerId", p.id);
                item.put("nickname", p.nickname);
                item.put("online", p.online);
                item.put("alive", p.alive);
                item.put("host", p.id.equals(room.hostPlayerId));
                return item;
            }).toList();

            Map<String,Object> result = new LinkedHashMap<>();
            result.put("roomId", room.roomId);
            result.put("gameType", "undercover");
            result.put("hostPlayerId", room.hostPlayerId);
            result.put("state", room.state.name());
            result.put("round", room.round);
            result.put("players", players);
            result.put("maxPlayers", MAX_PLAYERS);
            return result;
        }
    }

    public void cleanup() {
        long now = System.currentTimeMillis();
        rooms.entrySet().removeIf(e -> now - e.getValue().updatedAt > ROOM_TTL_MILLIS);
    }

    private Player newPlayer(WebSocketSession session, String nickname) {
        String value = nickname == null || nickname.isBlank() ? "玩家" : nickname.trim();
        if (value.length() > 12) value = value.substring(0, 12);
        return new Player(UUID.randomUUID().toString(), value, UUID.randomUUID().toString(), session);
    }

    private void requireUnbound(WebSocketSession session) {
        if (session == null || !session.isOpen()) throw new IllegalArgumentException("连接已失效");
        if (session.getAttributes().containsKey("roomId")
                || session.getAttributes().containsKey("drawGuessRoomId")
                || session.getAttributes().containsKey("eitherOrRoomId")
                || session.getAttributes().containsKey(ATTR_ROOM)) {
            throw new IllegalArgumentException("当前连接已经加入其他房间");
        }
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
        Object value = session.getAttributes().get(key);
        return value instanceof String ? (String) value : null;
    }

    private boolean constantTimeEquals(String a, String b) {
        return a != null && b != null && MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    private boolean expired(Room room) {
        return System.currentTimeMillis() - room.updatedAt > ROOM_TTL_MILLIS;
    }

    public static class Room {
        public final String roomId;
        public final Map<String,Player> players = new LinkedHashMap<>();
        public UndercoverState state = UndercoverState.WAITING;
        public String hostPlayerId;
        public int round;
        public long updatedAt = System.currentTimeMillis();

        public Room(String roomId) { this.roomId = roomId; }
        public void touch() { updatedAt = System.currentTimeMillis(); }
    }

    public static class Player {
        public final String id;
        public final String nickname;
        public final String reconnectToken;
        public WebSocketSession session;
        public boolean online = true;
        public boolean alive = true;

        public Player(String id, String nickname, String reconnectToken, WebSocketSession session) {
            this.id=id; this.nickname=nickname; this.reconnectToken=reconnectToken; this.session=session;
        }
    }
}
