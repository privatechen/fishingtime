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
    public static final int MIN_PLAYERS = 3;
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
        Player host = newPlayer(session, nickname, room);
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
            Player player = newPlayer(session, nickname, room);
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
            if (room.state == UndercoverState.SPEAKING
                    && playerId.equals(currentSpeakerId(room))) {
                advanceSpeakerLocked(room);
            }
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
            if (room.state == UndercoverState.SPEAKING
                    && playerId.equals(currentSpeakerId(room))) {
                advanceSpeakerLocked(room);
            }
            room.players.remove(playerId);
            room.votes.remove(playerId);
            room.votes.values().removeIf(playerId::equals);
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

    public List<Player> startGame(WebSocketSession session) {
        Room room = roomFor(session);
        synchronized (room) {
            String playerId = playerId(session);
            if (!playerId.equals(room.hostPlayerId)) {
                throw new IllegalArgumentException("只有房主可以开始游戏");
            }
            if (room.state != UndercoverState.WAITING) {
                throw new IllegalArgumentException("游戏已经开始");
            }
            List<Player> active = room.players.values().stream()
                    .filter(p -> p.online && p.session != null && p.session.isOpen())
                    .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
            if (active.size() < MIN_PLAYERS) {
                throw new IllegalArgumentException("至少需要 3 位在线玩家才能开始");
            }

            String[] pair = UndercoverWordBank.PAIRS.get(RANDOM.nextInt(UndercoverWordBank.PAIRS.size()));
            room.civilianWord = pair[0];
            room.undercoverWord = pair[1];
            int undercoverCount = active.size() <= 5 ? 1 : active.size() <= 8 ? 2 : 3;
            Collections.shuffle(active, RANDOM);
            for (int i = 0; i < active.size(); i++) {
                Player player = active.get(i);
                player.role = i < undercoverCount ? "UNDERCOVER" : "CIVILIAN";
                player.word = i < undercoverCount ? room.undercoverWord : room.civilianWord;
                player.ready = false;
                player.alive = true;
            }
            room.round = 1;
            room.state = UndercoverState.ROLE_REVEAL;
            room.touch();
            return new ArrayList<>(active);
        }
    }

    public boolean markReady(WebSocketSession session) {
        Room room = roomFor(session);
        synchronized (room) {
            if (room.state != UndercoverState.ROLE_REVEAL) {
                throw new IllegalArgumentException("当前不是查看词语阶段");
            }
            Player player = room.players.get(playerId(session));
            if (!player.online) throw new IllegalArgumentException("当前玩家已离线");
            player.ready = true;
            boolean allReady = room.players.values().stream()
                    .filter(p -> p.online)
                    .allMatch(p -> p.ready);
            if (allReady) {
                room.speakingOrder = room.players.values().stream()
                        .filter(p -> p.online && p.alive)
                        .map(p -> p.id)
                        .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
                Collections.shuffle(room.speakingOrder, RANDOM);
                room.speakingIndex = 0;
                room.state = UndercoverState.SPEAKING;
                room.speakingDeadline = System.currentTimeMillis() + 15_000L;
            }
            room.touch();
            return allReady;
        }
    }

    public boolean speakerDone(WebSocketSession session) {
        Room room = roomFor(session);
        synchronized (room) {
            if (room.state != UndercoverState.SPEAKING) {
                throw new IllegalArgumentException("当前不是描述阶段");
            }
            String playerId = playerId(session);
            if (!playerId.equals(currentSpeakerId(room))) {
                throw new IllegalArgumentException("现在还没轮到你描述");
            }
            advanceSpeakerLocked(room);
            return room.state == UndercoverState.VOTING;
        }
    }

    public boolean advanceSpeakerIfExpired(Room room, long now) {
        synchronized (room) {
            if (room.state != UndercoverState.SPEAKING || room.speakingDeadline > now) {
                return false;
            }
            advanceSpeakerLocked(room);
            return true;
        }
    }

    private void advanceSpeakerLocked(Room room) {
        room.speakingIndex++;
        while (room.speakingIndex < room.speakingOrder.size()) {
            Player next = room.players.get(room.speakingOrder.get(room.speakingIndex));
            if (next != null && next.online && next.alive) break;
            room.speakingIndex++;
        }
        if (room.speakingIndex >= room.speakingOrder.size()) {
            startVotingLocked(room);
        } else {
            room.speakingDeadline = System.currentTimeMillis() + 15_000L;
        }
        room.touch();
    }

    private String currentSpeakerId(Room room) {
        if (room.speakingIndex < 0 || room.speakingIndex >= room.speakingOrder.size()) return null;
        return room.speakingOrder.get(room.speakingIndex);
    }

    private void startVotingLocked(Room room) {
        room.state = UndercoverState.VOTING;
        room.votes.clear();
        room.voteDeadline = System.currentTimeMillis() + 15_000L;
        room.touch();
    }

    public Map<String,Object> submitVote(WebSocketSession session, String targetPlayerId) {
        Room room = roomFor(session);
        synchronized (room) {
            if (room.state != UndercoverState.VOTING) {
                throw new IllegalArgumentException("当前不是投票阶段");
            }
            String voterId = playerId(session);
            Player voter = room.players.get(voterId);
            Player target = room.players.get(targetPlayerId);
            if (voter == null || !voter.online || !voter.alive) {
                throw new IllegalArgumentException("当前玩家不能投票");
            }
            if (target == null || !target.alive || !target.online) {
                throw new IllegalArgumentException("请选择一位在线且未淘汰的玩家");
            }
            if (voterId.equals(targetPlayerId)) {
                throw new IllegalArgumentException("不能投给自己");
            }
            if (room.votes.containsKey(voterId)) {
                throw new IllegalArgumentException("你已经投过票了");
            }
            room.votes.put(voterId, targetPlayerId);
            room.touch();
            if (eligibleVoters(room).stream().allMatch(room.votes::containsKey)) {
                return resolveVoteLocked(room);
            }
            Map<String,Object> status = new LinkedHashMap<>();
            status.put("votedCount", room.votes.size());
            status.put("eligibleCount", eligibleVoters(room).size());
            status.put("allVoted", false);
            return status;
        }
    }

    public Map<String,Object> resolveVoteIfExpired(Room room, long now) {
        synchronized (room) {
            if (room.state != UndercoverState.VOTING || room.voteDeadline > now) return null;
            return resolveVoteLocked(room);
        }
    }

    public Map<String,Object> resolveVote(Room room) {
        synchronized (room) {
            if (room.state != UndercoverState.VOTING) {
                throw new IllegalArgumentException("投票结果已经产生");
            }
            return resolveVoteLocked(room);
        }
    }

    private List<String> eligibleVoters(Room room) {
        return room.players.values().stream()
                .filter(p -> p.online && p.alive)
                .map(p -> p.id)
                .toList();
    }

    private Map<String,Object> resolveVoteLocked(Room room) {
        Map<String,Integer> tally = new LinkedHashMap<>();
        for (String targetId : room.votes.values()) tally.merge(targetId, 1, Integer::sum);
        int highest = tally.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        List<String> leaders = tally.entrySet().stream()
                .filter(e -> e.getValue() == highest && highest > 0)
                .map(Map.Entry::getKey)
                .toList();
        String eliminatedId = leaders.size() == 1 ? leaders.get(0) : null;
        Player eliminated = eliminatedId == null ? null : room.players.get(eliminatedId);
        if (eliminated != null) eliminated.alive = false;

        long undercoverAlive = room.players.values().stream()
                .filter(p -> p.alive && "UNDERCOVER".equals(p.role)).count();
        long civilianAlive = room.players.values().stream()
                .filter(p -> p.alive && "CIVILIAN".equals(p.role)).count();
        String winner = null;
        if (undercoverAlive == 0) winner = "CIVILIAN";
        else if (undercoverAlive >= civilianAlive) winner = "UNDERCOVER";

        room.state = winner == null ? UndercoverState.ELIMINATION : UndercoverState.GAME_OVER;
        room.voteDeadline = 0L;
        room.touch();

        List<Map<String,Object>> results = room.players.values().stream()
                .filter(p -> p.alive || p.id.equals(eliminatedId))
                .map(p -> {
                    Map<String,Object> item = new LinkedHashMap<>();
                    item.put("playerId", p.id);
                    item.put("nickname", p.nickname);
                    item.put("votes", tally.getOrDefault(p.id, 0));
                    item.put("eliminated", p.id.equals(eliminatedId));
                    item.put("role", p.id.equals(eliminatedId) ? p.role : null);
                    return item;
                })
                .toList();
        Map<String,Object> result = new LinkedHashMap<>();
        result.put("eliminatedPlayerId", eliminatedId);
        result.put("eliminatedNickname", eliminated == null ? null : eliminated.nickname);
        result.put("eliminatedRole", eliminated == null ? null : eliminated.role);
        result.put("tie", eliminatedId == null);
        result.put("winner", winner);
        result.put("gameOver", winner != null);
        result.put("results", results);
        result.put("round", room.round);
        result.put("allVoted", true);
        room.lastVoteResult = result;
        return result;
    }

    public Map<String,Object> lastVoteResult(Room room) {
        synchronized (room) { return room.lastVoteResult; }
    }

    /**
     * A player can return the completed game to its room lobby.
     * The room and player identities are preserved so the same group can play again.
     */
    public void returnToWaitingRoom(WebSocketSession session) {
        Room room = roomFor(session);
        synchronized (room) {
            if (room.state != UndercoverState.GAME_OVER) {
                throw new IllegalArgumentException("只有游戏结束后才能返回房间");
            }
            for (Player player : room.players.values()) {
                player.alive = true;
                player.ready = false;
                player.role = null;
                player.word = null;
            }
            room.civilianWord = null;
            room.undercoverWord = null;
            room.round = 0;
            room.speakingOrder = new ArrayList<>();
            room.speakingIndex = 0;
            room.speakingDeadline = 0L;
            room.voteDeadline = 0L;
            room.votes.clear();
            room.lastVoteResult = null;
            room.state = UndercoverState.WAITING;
            room.touch();
        }
    }

    public void startNextRound(WebSocketSession session) {
        Room room = roomFor(session);
        synchronized (room) {
            if (!playerId(session).equals(room.hostPlayerId)) {
                throw new IllegalArgumentException("只有房主可以开始下一轮");
            }
            if (room.state != UndercoverState.ELIMINATION) {
                throw new IllegalArgumentException("当前不能开始下一轮");
            }
            List<String> alive = room.players.values().stream()
                    .filter(p -> p.online && p.alive)
                    .map(p -> p.id)
                    .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
            if (alive.size() < MIN_PLAYERS) {
                throw new IllegalArgumentException("存活玩家不足，无法继续游戏");
            }
            Collections.shuffle(alive, RANDOM);
            room.speakingOrder = alive;
            room.speakingIndex = 0;
            room.votes.clear();
            room.lastVoteResult = null;
            room.round++;
            room.state = UndercoverState.SPEAKING;
            room.speakingDeadline = System.currentTimeMillis() + 15_000L;
            room.voteDeadline = 0L;
            room.touch();
        }
    }

    public boolean hasVoted(Room room, String playerId) {
        synchronized (room) { return room.votes.containsKey(playerId); }
    }

    public List<Player> players(Room room) {
        synchronized (room) {
            return new ArrayList<>(room.players.values());
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
                item.put("ready", p.ready);
                item.put("host", p.id.equals(room.hostPlayerId));
                return item;
            }).toList();

            Map<String,Object> result = new LinkedHashMap<>();
            result.put("roomId", room.roomId);
            result.put("gameType", "undercover");
            result.put("hostPlayerId", room.hostPlayerId);
            result.put("state", room.state.name());
            result.put("round", room.round);
            result.put("allReady", room.players.values().stream().filter(p -> p.online).allMatch(p -> p.ready));
            result.put("speakingOrder", room.speakingOrder);
            result.put("speakingIndex", room.speakingIndex);
            result.put("currentSpeakerId", currentSpeakerId(room));
            result.put("speakingDeadline", room.speakingDeadline);
            result.put("voteDeadline", room.voteDeadline);
            result.put("votedCount", room.votes.size());
            result.put("eligibleVoterCount", eligibleVoters(room).size());
            result.put("serverTime", System.currentTimeMillis());
            result.put("players", players);
            result.put("maxPlayers", MAX_PLAYERS);
            return result;
        }
    }

    public void cleanup() {
        long now = System.currentTimeMillis();
        rooms.entrySet().removeIf(e -> now - e.getValue().updatedAt > ROOM_TTL_MILLIS);
    }

    private Player newPlayer(WebSocketSession session, String nickname, Room room) {
        String requested = nickname == null || nickname.isBlank() ? "玩家" : nickname.trim();
        if (requested.length() > 12) requested = requested.substring(0, 12);

        Set<String> existingNames = room.players.values().stream()
                .map(existing -> existing.nickname)
                .collect(java.util.stream.Collectors.toSet());
        String value = requested;
        if (existingNames.contains(requested)) {
            int suffix = 1;
            do {
                value = "玩家" + suffix++;
            } while (existingNames.contains(value));
        }
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
        public String civilianWord;
        public String undercoverWord;
        public List<String> speakingOrder = new ArrayList<>();
        public int speakingIndex;
        public long speakingDeadline;
        public long voteDeadline;
        public final Map<String,String> votes = new LinkedHashMap<>();
        public Map<String,Object> lastVoteResult;
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
        public boolean ready;
        public String role;
        public String word;

        public Player(String id, String nickname, String reconnectToken, WebSocketSession session) {
            this.id=id; this.nickname=nickname; this.reconnectToken=reconnectToken; this.session=session;
        }
    }
}
