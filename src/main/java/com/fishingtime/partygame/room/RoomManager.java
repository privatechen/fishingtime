package com.fishingtime.partygame.room;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RoomManager {
    public static final int SIZE = 15;
    private static final long ROOM_TTL_MILLIS = 30 * 60 * 1000L;
    private static final long REMATCH_TTL_MILLIS = 30 * 1000L;
    private static final SecureRandom RANDOM = new SecureRandom();
    private final Map<String, Room> rooms = new ConcurrentHashMap<>();

    public synchronized Room create(WebSocketSession session, String nickname) {
        String code;
        do {
            code = String.format("%04d", RANDOM.nextInt(10000));
        } while (rooms.containsKey(code));
        Room room = new Room(code);
        String playerId = UUID.randomUUID().toString();
        room.players.put(playerId, new Player(playerId, safeName(nickname), 1, session));
        room.updatedAt = System.currentTimeMillis();
        rooms.put(code, room);
        session.getAttributes().put("roomId", code);
        session.getAttributes().put("playerId", playerId);
        return room;
    }

    public synchronized Room join(WebSocketSession session, String code, String nickname) {
        Room room = rooms.get(code);
        if (room == null || expired(room)) throw new IllegalArgumentException("房间不存在或已过期");
        if (room.players.size() >= 2) throw new IllegalArgumentException("房间已满");
        if (room.players.values().stream().anyMatch(p -> p.session.isOpen() && p.session.getId().equals(session.getId()))) {
            throw new IllegalArgumentException("你已经在房间中");
        }
        String playerId = UUID.randomUUID().toString();
        room.players.put(playerId, new Player(playerId, safeName(nickname), 2, session));
        room.updatedAt = System.currentTimeMillis();
        session.getAttributes().put("roomId", code);
        session.getAttributes().put("playerId", playerId);
        room.started = true;
        return room;
    }

    public Room get(String code) {
        Room room = rooms.get(code);
        if (room == null || expired(room)) throw new IllegalArgumentException("房间不存在或已过期");
        return room;
    }

    public synchronized MoveResult move(WebSocketSession session, int x, int y) {
        String code = (String) session.getAttributes().get("roomId");
        String playerId = (String) session.getAttributes().get("playerId");
        Room room = get(code);
        synchronized (room) {
            Player player = room.players.get(playerId);
            if (player == null || !room.started) throw new IllegalArgumentException("房间尚未开始");
            if (room.winner != 0 || room.draw) throw new IllegalArgumentException("本局已经结束");
            if (player.color != room.currentPlayer) throw new IllegalArgumentException("还没有轮到你");
            if (x < 0 || x >= SIZE || y < 0 || y >= SIZE) throw new IllegalArgumentException("落子位置无效");
            if (room.board[y][x] != 0) throw new IllegalArgumentException("这个位置已经有棋子了");
            int color = room.currentPlayer;
            room.board[y][x] = color;
            room.moves++;
            room.lastX = x;
            room.lastY = y;
            room.lastColor = color;
            if (hasWon(room.board, x, y, color)) room.winner = color;
            else if (room.moves == SIZE * SIZE) room.draw = true;
            else room.currentPlayer = color == 1 ? 2 : 1;
            room.updatedAt = System.currentTimeMillis();
            return new MoveResult(room, playerId);
        }
    }

    public synchronized RematchResult requestRematch(WebSocketSession session) {
        Room room = getSessionRoom(session);
        synchronized (room) {
            requireFinishedTwoPlayerGame(room);
            String playerId = (String) session.getAttributes().get("playerId");
            long now = System.currentTimeMillis();
            if (room.rematchInviterId != null && now - room.rematchRequestedAt > REMATCH_TTL_MILLIS) {
                room.rematchInviterId = null;
                room.rematchRequestedAt = 0;
            }
            if (room.rematchInviterId != null && !room.rematchInviterId.equals(playerId)) {
                restartBoard(room);
                room.rematchInviterId = null;
                room.rematchRequestedAt = 0;
                return new RematchResult(room, "RESTARTED");
            }
            if (playerId.equals(room.rematchInviterId)) {
                throw new IllegalArgumentException("再战邀请已发送，请等待对方回应");
            }
            room.rematchInviterId = playerId;
            room.rematchRequestedAt = now;
            return new RematchResult(room, "REQUESTED");
        }
    }

    public synchronized RematchResult respondRematch(WebSocketSession session, boolean accept) {
        Room room = getSessionRoom(session);
        synchronized (room) {
            requireFinishedTwoPlayerGame(room);
            String playerId = (String) session.getAttributes().get("playerId");
            long now = System.currentTimeMillis();
            if (room.rematchInviterId == null || now - room.rematchRequestedAt > REMATCH_TTL_MILLIS) {
                room.rematchInviterId = null;
                room.rematchRequestedAt = 0;
                throw new IllegalArgumentException("再战邀请已失效");
            }
            if (room.rematchInviterId.equals(playerId)) throw new IllegalArgumentException("不能回应自己的邀请");
            if (accept) restartBoard(room);
            room.rematchInviterId = null;
            room.rematchRequestedAt = 0;
            return new RematchResult(room, accept ? "ACCEPTED" : "REJECTED");
        }
    }

    public synchronized RematchResult cancelRematch(WebSocketSession session) {
        Room room = getSessionRoom(session);
        synchronized (room) {
            String playerId = (String) session.getAttributes().get("playerId");
            if (!playerId.equals(room.rematchInviterId)) throw new IllegalArgumentException("没有可取消的再战邀请");
            room.rematchInviterId = null;
            room.rematchRequestedAt = 0;
            return new RematchResult(room, "CANCELLED");
        }
    }

    private Room getSessionRoom(WebSocketSession session) {
        String code = (String) session.getAttributes().get("roomId");
        String playerId = (String) session.getAttributes().get("playerId");
        Room room = get(code);
        if (playerId == null || !room.players.containsKey(playerId)) throw new IllegalArgumentException("你不在这个房间中");
        return room;
    }

    private void requireFinishedTwoPlayerGame(Room room) {
        if (room.players.size() != 2 || !room.started) throw new IllegalArgumentException("等待另一位玩家加入");
        if (room.winner == 0 && !room.draw) throw new IllegalArgumentException("本局尚未结束");
    }

    private void restartBoard(Room room) {
        room.board = new int[SIZE][SIZE];
        room.currentPlayer = 1;
        room.moves = 0;
        room.lastX = -1;
        room.lastY = -1;
        room.lastColor = 0;
        room.winner = 0;
        room.draw = false;
        room.updatedAt = System.currentTimeMillis();
    }

    public synchronized Room leave(WebSocketSession session) {
        String code = (String) session.getAttributes().get("roomId");
        String playerId = (String) session.getAttributes().get("playerId");
        if (code == null) return null;
        Room room = rooms.get(code);
        if (room == null) return null;
        room.players.remove(playerId);
        room.updatedAt = System.currentTimeMillis();
        if (room.players.isEmpty()) rooms.remove(code);
        room.rematchInviterId = null;
        room.rematchRequestedAt = 0;
        session.getAttributes().remove("roomId");
        session.getAttributes().remove("playerId");
        return room;
    }

    public void cleanup() {
        long now = System.currentTimeMillis();
        rooms.entrySet().removeIf(e -> now - e.getValue().updatedAt > ROOM_TTL_MILLIS);
    }

    public Map<String, Object> snapshot(Room room) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("roomId", room.code);
        result.put("started", room.started);
        result.put("players", room.players.values().stream().map(p -> Map.of(
                "playerId", p.id, "nickname", p.nickname, "color", p.color,
                "online", p.session.isOpen())).toList());
        result.put("board", room.board);
        result.put("currentPlayer", room.currentPlayer);
        result.put("moves", room.moves);
        result.put("lastMove", room.lastX < 0 ? null : Map.of("x", room.lastX, "y", room.lastY, "color", room.lastColor));
        result.put("winner", room.winner == 0 ? null : room.winner);
        result.put("draw", room.draw);
        return result;
    }

    public Collection<WebSocketSession> sessions(Room room) {
        return room.players.values().stream().map(p -> p.session).toList();
    }

    public void markDisconnected(WebSocketSession session) {
        String code = (String) session.getAttributes().get("roomId");
        if (code == null) return;
        Room room = rooms.get(code);
        if (room != null) room.updatedAt = System.currentTimeMillis();
    }

    private boolean expired(Room room) {
        return System.currentTimeMillis() - room.updatedAt > ROOM_TTL_MILLIS;
    }

    private static String safeName(String name) {
        if (name == null || name.isBlank()) return "玩家";
        String value = name.trim();
        return value.length() > 12 ? value.substring(0, 12) : value;
    }

    private static boolean hasWon(int[][] board, int x, int y, int color) {
        int[][] directions = {{1,0},{0,1},{1,1},{1,-1}};
        for (int[] d : directions) {
            int count = 1 + count(board, x, y, d[0], d[1], color)
                    + count(board, x, y, -d[0], -d[1], color);
            if (count >= 5) return true;
        }
        return false;
    }

    private static int count(int[][] board, int x, int y, int dx, int dy, int color) {
        int n = 0, cx = x + dx, cy = y + dy;
        while (cx >= 0 && cx < SIZE && cy >= 0 && cy < SIZE && board[cy][cx] == color) {
            n++; cx += dx; cy += dy;
        }
        return n;
    }

    public static class Room {
        public final String code;
        public final Map<String, Player> players = new LinkedHashMap<>();
        public int[][] board = new int[SIZE][SIZE];
        public int currentPlayer = 1;
        public int moves;
        public int lastX = -1, lastY = -1, lastColor;
        public int winner;
        public boolean draw;
        public boolean started;
        public long updatedAt;
        public String rematchInviterId;
        public long rematchRequestedAt;
        Room(String code) { this.code = code; }
    }

    public static class Player {
        public final String id, nickname;
        public final int color;
        public final WebSocketSession session;
        Player(String id, String nickname, int color, WebSocketSession session) {
            this.id=id; this.nickname=nickname; this.color=color; this.session=session;
        }
    }

    public record MoveResult(Room room, String playerId) {}
    public record RematchResult(Room room, String status) {}
}
