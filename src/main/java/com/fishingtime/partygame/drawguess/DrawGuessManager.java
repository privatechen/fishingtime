package com.fishingtime.partygame.drawguess;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Independent in-memory room manager for Draw & Guess.
 * It deliberately does not reuse Gomoku's two-player RoomManager.
 */
@Component
public class DrawGuessManager {
    public static final int MIN_PLAYERS = 2;
    public static final int MAX_PLAYERS = 8;
    private static final long ROOM_TTL_MILLIS = 30 * 60 * 1000L;
    private static final SecureRandom RANDOM = new SecureRandom();

    private static final String ATTR_ROOM_ID = "drawGuessRoomId";
    private static final String ATTR_PLAYER_ID = "drawGuessPlayerId";

    private final Map<String, DrawGuessRoom> rooms = new ConcurrentHashMap<>();
    private final DrawGuessWordRepository wordRepository;

    public DrawGuessManager(DrawGuessWordRepository wordRepository) {
        this.wordRepository = wordRepository;
    }

    public synchronized DrawGuessRoom create(WebSocketSession session, String nickname) {
        requireUnboundSession(session);
        String roomId;
        do {
            roomId = String.format("%04d", RANDOM.nextInt(10000));
        } while (rooms.containsKey(roomId));

        DrawGuessRoom room = new DrawGuessRoom(roomId);
        DrawGuessPlayer host = newPlayer(session, nickname);
        room.getPlayers().put(host.getId(), host);
        room.setHostPlayerId(host.getId());
        rooms.put(roomId, room);
        bindSession(session, roomId, host.getId());
        room.touch();
        return room;
    }

    public synchronized DrawGuessRoom join(WebSocketSession session, String roomId, String nickname) {
        requireUnboundSession(session);
        DrawGuessRoom room = get(roomId);
        synchronized (room) {
            if (room.getState() != DrawGuessState.WAITING) {
                throw new IllegalArgumentException("游戏已经开始，无法加入");
            }
            if (room.getPlayers().size() >= MAX_PLAYERS) {
                throw new IllegalArgumentException("房间已满");
            }
            if (room.getPlayers().values().stream().anyMatch(p ->
                    p.getSession() != null && p.getSession().getId().equals(session.getId()))) {
                throw new IllegalArgumentException("你已经在房间中");
            }
            DrawGuessPlayer player = newPlayer(session, nickname);
            room.getPlayers().put(player.getId(), player);
            bindSession(session, roomId, player.getId());
            room.touch();
            return room;
        }
    }

    public DrawGuessRoom get(String roomId) {
        if (roomId == null || roomId.isBlank()) throw new IllegalArgumentException("缺少房间号");
        DrawGuessRoom room = rooms.get(roomId);
        if (room == null || expired(room)) throw new IllegalArgumentException("房间不存在或已过期");
        return room;
    }

    public synchronized DisconnectInfo disconnect(WebSocketSession session) {
        String roomId = attribute(session, ATTR_ROOM_ID);
        String playerId = attribute(session, ATTR_PLAYER_ID);
        if (roomId == null || playerId == null) return null;
        DrawGuessRoom room = rooms.get(roomId);
        clearSessionBinding(session);
        if (room == null) return null;
        synchronized (room) {
            DrawGuessPlayer player = room.getPlayers().get(playerId);
            if (player == null || player.getSession() == null
                    || !player.getSession().getId().equals(session.getId())) return null;
            player.setOnline(false);
            player.setSession(null);
            if (playerId.equals(room.getHostPlayerId())) {
                room.setHostPlayerId(room.getPlayers().values().stream()
                        .filter(DrawGuessPlayer::isOnline).map(DrawGuessPlayer::getId)
                        .findFirst().orElse(playerId));
            }
            room.touch();
            return new DisconnectInfo(room, playerId, playerId.equals(room.getArtistId()), room.getState());
        }
    }

    public synchronized DrawGuessPlayer reconnect(WebSocketSession session, String roomId,
                                                   String playerId, String reconnectToken) {
        requireUnboundSession(session);
        DrawGuessRoom room = get(roomId);
        synchronized (room) {
            DrawGuessPlayer player = room.getPlayers().get(playerId);
            if (player == null || reconnectToken == null
                    || !java.security.MessageDigest.isEqual(
                    player.getReconnectToken().getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    reconnectToken.getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
                throw new IllegalArgumentException("重连凭证无效");
            }
            if (player.isOnline()) throw new IllegalArgumentException("该玩家已有有效连接");
            player.setSession(session);
            player.setOnline(true);
            bindSession(session, roomId, playerId);
            room.touch();
            return player;
        }
    }

    public record DisconnectInfo(DrawGuessRoom room, String playerId, boolean artist, DrawGuessState state) {}

    public synchronized DrawGuessRoom leave(WebSocketSession session) {
        String roomId = attribute(session, ATTR_ROOM_ID);
        String playerId = attribute(session, ATTR_PLAYER_ID);
        if (roomId == null || playerId == null) return null;

        DrawGuessRoom room = rooms.get(roomId);
        clearSessionBinding(session);
        if (room == null) return null;

        synchronized (room) {
            room.getPlayers().remove(playerId);
            if (room.getPlayers().isEmpty()) {
                rooms.remove(roomId, room);
                return room;
            }
            if (playerId.equals(room.getHostPlayerId())) {
                room.setHostPlayerId(room.getPlayers().values().stream()
                        .filter(DrawGuessPlayer::isOnline)
                        .map(DrawGuessPlayer::getId)
                        .findFirst()
                        .orElseGet(() -> room.getPlayers().keySet().iterator().next()));
            }
            room.touch();
            return room;
        }
    }

    public DrawGuessPlayer playerForSession(WebSocketSession session) {
        String roomId = attribute(session, ATTR_ROOM_ID);
        String playerId = attribute(session, ATTR_PLAYER_ID);
        if (roomId == null || playerId == null) return null;
        DrawGuessRoom room = rooms.get(roomId);
        if (room == null) return null;
        synchronized (room) {
            DrawGuessPlayer player = room.getPlayers().get(playerId);
            return player != null && player.getSession() != null
                    && player.getSession().getId().equals(session.getId()) ? player : null;
        }
    }

    public List<WebSocketSession> sessions(DrawGuessRoom room) {
        List<WebSocketSession> result = new ArrayList<>();
        synchronized (room) {
            for (DrawGuessPlayer player : room.getPlayers().values()) {
                WebSocketSession session = player.getSession();
                if (player.isOnline() && session != null) result.add(session);
            }
        }
        return result;
    }

    /**
     * Public snapshot only. Secret word and reconnect tokens are deliberately
     * excluded; the future message handler must send those privately.
     */
    public Map<String, Object> snapshot(DrawGuessRoom room) {
        synchronized (room) {
            List<Map<String, Object>> players = room.getPlayers().values().stream().map(player -> {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("playerId", player.getId());
                item.put("nickname", player.getNickname());
                item.put("online", player.isOnline());
                item.put("totalScore", player.getTotalScore());
                return item;
            }).toList();

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("roomId", room.getRoomId());
            result.put("gameType", "draw-guess");
            result.put("hostPlayerId", room.getHostPlayerId());
            result.put("state", room.getState().name());
            result.put("currentRound", room.getCurrentRound());
            result.put("artistId", room.getArtistId());
            result.put("totalRounds", room.getTotalRounds());
            result.put("players", players);
            result.put("maxPlayers", MAX_PLAYERS);
            return result;
        }
    }

    public Collection<DrawGuessRoom> allRooms() {
        return List.copyOf(rooms.values());
    }

    public void cleanup() {
        long now = System.currentTimeMillis();
        rooms.entrySet().removeIf(entry -> now - entry.getValue().getUpdatedAt() > ROOM_TTL_MILLIS);
    }

    private DrawGuessPlayer newPlayer(WebSocketSession session, String nickname) {
        String playerId = UUID.randomUUID().toString();
        return new DrawGuessPlayer(playerId, safeName(nickname), UUID.randomUUID().toString(), session);
    }

    private void requireUnboundSession(WebSocketSession session) {
        if (session == null || !session.isOpen()) throw new IllegalArgumentException("连接已失效");
        if (session.getAttributes().containsKey("roomId")
                || session.getAttributes().containsKey(ATTR_ROOM_ID)) {
            throw new IllegalArgumentException("当前连接已经加入其他房间");
        }
    }

    private void bindSession(WebSocketSession session, String roomId, String playerId) {
        session.getAttributes().put(ATTR_ROOM_ID, roomId);
        session.getAttributes().put(ATTR_PLAYER_ID, playerId);
    }

    private void clearSessionBinding(WebSocketSession session) {
        session.getAttributes().remove(ATTR_ROOM_ID);
        session.getAttributes().remove(ATTR_PLAYER_ID);
    }

    private String attribute(WebSocketSession session, String key) {
        Object value = session.getAttributes().get(key);
        return value instanceof String ? (String) value : null;
    }

    private boolean expired(DrawGuessRoom room) {
        return System.currentTimeMillis() - room.getUpdatedAt() > ROOM_TTL_MILLIS;
    }

    private static String safeName(String name) {
        if (name == null || name.isBlank()) return "玩家";
        String value = name.trim();
        return value.length() > 12 ? value.substring(0, 12) : value;
    }
}
