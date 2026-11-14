package com.fishingtime.partygame.eitheror;

import org.springframework.stereotype.Service;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class EitherOrGameService {
    public static final long VOTE_DURATION_MILLIS = 10_000L;
    private final EitherOrManager manager;
    private final EitherOrQuestionRepository questions;

    public EitherOrGameService(EitherOrManager manager, EitherOrQuestionRepository questions) {
        this.manager = manager;
        this.questions = questions;
    }

    public QuestionStarted start(String roomId, String playerId, long now) {
        EitherOrManager.Room room = manager.get(roomId);
        synchronized (room) {
            requireHost(room, playerId);
            if (room.state != EitherOrManager.State.WAITING) throw new IllegalArgumentException("游戏已经开始");
            requireOnlinePlayers(room);
            room.questions = questions.drawTen();
            room.round = 0;
            return startNextQuestion(room, now);
        }
    }

    public QuestionStarted next(String roomId, String playerId, long now) {
        EitherOrManager.Room room = manager.get(roomId);
        synchronized (room) {
            requireHost(room, playerId);
            if (room.state != EitherOrManager.State.RESULT) throw new IllegalArgumentException("当前不能进入下一题");
            if (room.round >= room.totalRounds) {
                room.state = EitherOrManager.State.GAME_OVER;
                room.deadline = 0;
                room.touch();
                return null;
            }
            return startNextQuestion(room, now);
        }
    }

    public VoteSubmission submit(String roomId, String playerId, String choice, long now) {
        EitherOrManager.Room room = manager.get(roomId);
        synchronized (room) {
            if (room.state != EitherOrManager.State.VOTING) throw new IllegalArgumentException("当前不在投票阶段");
            if (now >= room.deadline) throw new IllegalArgumentException("本题投票时间已结束");
            if (!"A".equals(choice) && !"B".equals(choice)) throw new IllegalArgumentException("请选择 A 或 B");
            EitherOrManager.Player player = room.players.get(playerId);
            if (player == null || !player.online) throw new IllegalArgumentException("玩家不在当前房间");
            if (player.choice != null) throw new IllegalArgumentException("本题已经提交过选择");
            player.choice = choice;
            room.touch();
            if (allOnlinePlayersVoted(room)) {
                return new VoteSubmission(null, finishRound(room));
            }
            return new VoteSubmission(progress(room), null);
        }
    }

    public List<RoundResult> processTimers(long now) {
        List<RoundResult> completed = new ArrayList<>();
        for (EitherOrManager.Room room : manager.allRooms()) {
            synchronized (room) {
                if (room.state == EitherOrManager.State.VOTING
                        && (now >= room.deadline || allOnlinePlayersVoted(room))) {
                    completed.add(finishRound(room));
                }
            }
        }
        return completed;
    }

    public Map<String,Object> progress(EitherOrManager.Room room) {
        synchronized (room) {
            long online = room.players.values().stream().filter(p -> p.online).count();
            long submitted = room.players.values().stream().filter(p -> p.online && p.choice != null).count();
            return Map.of("submitted", submitted, "online", online, "deadline", room.deadline);
        }
    }

    private QuestionStarted startNextQuestion(EitherOrManager.Room room, long now) {
        room.round++;
        room.currentQuestion = room.questions.get(room.round - 1);
        room.lastResult = null;
        room.deadline = now + VOTE_DURATION_MILLIS;
        room.state = EitherOrManager.State.VOTING;
        for (EitherOrManager.Player player : room.players.values()) player.choice = null;
        room.touch();
        return new QuestionStarted(room.round, room.totalRounds, room.deadline, room.currentQuestion,
                manager.snapshot(room));
    }

    private RoundResult finishRound(EitherOrManager.Room room) {
        int votesA = (int) room.players.values().stream().filter(p -> "A".equals(p.choice)).count();
        int votesB = (int) room.players.values().stream().filter(p -> "B".equals(p.choice)).count();
        String minority = votesA + votesB < 2 || votesA == votesB ? "TIE" : (votesA < votesB ? "A" : "B");
        Map<String,Integer> roundPoints = new HashMap<>();
        if (!"TIE".equals(minority) && votesA + votesB >= 2) {
            for (EitherOrManager.Player player : room.players.values()) {
                if (minority.equals(player.choice)) {
                    player.score++;
                    roundPoints.put(player.id, 1);
                }
            }
        }
        List<Map<String,Object>> scores = room.players.values().stream()
                .sorted(Comparator.comparingInt((EitherOrManager.Player p) -> p.score).reversed()
                        .thenComparing(p -> p.nickname))
                .map(p -> {
                    Map<String,Object> item = new LinkedHashMap<>();
                    item.put("playerId", p.id); item.put("nickname", p.nickname);
                    item.put("score", p.score); item.put("roundPoints", roundPoints.getOrDefault(p.id, 0));
                    item.put("choice", p.choice); item.put("online", p.online);
                    return item;
                }).collect(Collectors.toList());
        room.state = EitherOrManager.State.RESULT;
        room.deadline = 0;
        room.resultAt = System.currentTimeMillis();
        room.touch();
        Map<String,Object> snapshot = manager.snapshot(room);
        RoundResult result = new RoundResult(room.round, votesA, votesB, minority, scores, snapshot, "RESULT");
        Map<String,Object> saved = new LinkedHashMap<>();
        saved.put("round", result.round()); saved.put("votesA", result.votesA());
        saved.put("votesB", result.votesB()); saved.put("minority", result.minority());
        saved.put("scores", result.scores()); saved.put("nextState", "RESULT");
        room.lastResult = saved;
        return result;
    }

    private boolean allOnlinePlayersVoted(EitherOrManager.Room room) {
        List<EitherOrManager.Player> online = room.players.values().stream().filter(p -> p.online).toList();
        return !online.isEmpty() && online.stream().allMatch(p -> p.choice != null);
    }

    private void requireOnlinePlayers(EitherOrManager.Room room) {
        long count = room.players.values().stream().filter(p -> p.online).count();
        if (count < EitherOrManager.MIN_PLAYERS) throw new IllegalArgumentException("至少需要两名在线玩家");
    }

    private void requireHost(EitherOrManager.Room room, String playerId) {
        if (playerId == null || !playerId.equals(room.hostPlayerId))
            throw new IllegalArgumentException("只有房主可以执行此操作");
    }

    public record QuestionStarted(int round, int totalRounds, long deadline,
                                  EitherOrManager.Question question, Map<String,Object> room) {}
    public record VoteSubmission(Map<String,Object> progress, RoundResult result) {}
    public record RoundResult(int round, int votesA, int votesB, String minority,
                              List<Map<String,Object>> scores, Map<String,Object> room, String nextState) {}
}
