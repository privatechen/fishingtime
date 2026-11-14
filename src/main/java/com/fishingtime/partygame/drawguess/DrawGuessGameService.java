package com.fishingtime.partygame.drawguess;

import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
public class DrawGuessGameService {
    public static final long SELECTION_TIMEOUT_MILLIS = 15_000L;
    public static final long ROUND_DURATION_MILLIS = 40_000L;
    public static final int ARTIST_BONUS_POINTS = 5;
    private static final int[] RANK_POINTS = {10, 8, 6, 4, 2};
    private static final int REMAINING_RANK_POINTS = 1;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final DrawGuessManager manager;
    private final DrawGuessWordRepository words;

    public DrawGuessGameService(DrawGuessManager manager, DrawGuessWordRepository words) {
        this.manager = manager;
        this.words = words;
    }

    public RoundSetup startGame(String roomId, String playerId, long now) {
        DrawGuessRoom room = manager.get(roomId);
        synchronized (room) {
            requireHost(room, playerId);
            requireState(room, DrawGuessState.WAITING);
            long online = room.getPlayers().values().stream().filter(DrawGuessPlayer::isOnline).count();
            if (online < DrawGuessManager.MIN_PLAYERS) throw new IllegalArgumentException("至少需要两名在线玩家");
            room.setCurrentRound(1);
            return beginRound(room, now);
        }
    }

    public RoundSetup startRematch(String roomId, long now) {
        DrawGuessRoom room = manager.get(roomId);
        synchronized (room) {
            requireState(room, DrawGuessState.GAME_OVER);
            long online = room.getPlayers().values().stream().filter(DrawGuessPlayer::isOnline).count();
            if (online < DrawGuessManager.MIN_PLAYERS) throw new IllegalArgumentException("至少需要两名在线玩家");
            for (DrawGuessPlayer player : room.getPlayers().values()) player.setTotalScore(0);
            room.setCurrentRound(1);
            room.getArtists().clear();
            room.getUsedWordIds().clear();
            room.clearRematch();
            return beginRound(room, now);
        }
    }

    public RoundSetup nextRound(String roomId, String playerId, long now) {
        DrawGuessRoom room = manager.get(roomId);
        synchronized (room) {
            requireHost(room, playerId);
            requireState(room, DrawGuessState.ROUND_RESULT);
            if (room.getCurrentRound() >= room.getTotalRounds()) throw new IllegalArgumentException("本局已经结束");
            room.setCurrentRound(room.getCurrentRound() + 1);
            return beginRound(room, now);
        }
    }

    public SelectedWord selectWord(String roomId, String playerId, int wordId, long now) {
        DrawGuessRoom room = manager.get(roomId);
        synchronized (room) {
            requireState(room, DrawGuessState.SELECTING);
            if (!playerId.equals(room.getArtistId())) throw new IllegalArgumentException("只有画师可以选择题目");
            DrawGuessWordRepository.Word selected = room.getWordOptions().stream()
                    .filter(option -> option.id() == wordId).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("请选择系统提供的题目"));
            return startDrawing(room, selected, now, false);
        }
    }

    public SelectedWord processSelectionTimeout(String roomId, long now) {
        DrawGuessRoom room = manager.get(roomId);
        synchronized (room) {
            if (room.getState() != DrawGuessState.SELECTING || now < room.getSelectionDeadline()) return null;
            DrawGuessWordRepository.Word selected = room.getWordOptions().get(RANDOM.nextInt(room.getWordOptions().size()));
            return startDrawing(room, selected, now, true);
        }
    }

    /**
     * Exact string matching by design: no trimming, case folding, aliases or fuzzy matching.
     * The first correct submission wins the next rank. This method is synchronized
     * on the room, making rank assignment and score updates atomic.
     */
    public GuessResult submitGuess(String roomId, String playerId, String answer, long now) {
        DrawGuessRoom room = manager.get(roomId);
        synchronized (room) {
            requireState(room, DrawGuessState.DRAWING);
            if (now >= room.getRoundDeadline()) throw new IllegalArgumentException("本轮猜题时间已结束");
            DrawGuessPlayer player = requirePlayer(room, playerId);
            if (playerId.equals(room.getArtistId())) throw new IllegalArgumentException("画师不能参与猜题");
            if (player.isGuessedCorrectly()) throw new IllegalArgumentException("你已经猜中答案");
            if (answer == null || !answer.equals(room.getCurrentAnswer())) {
                return new GuessResult(false, playerId, 0, 0, 0, false);
            }

            int rank = (int) room.getPlayers().values().stream()
                    .filter(p -> !p.getId().equals(room.getArtistId()) && p.isGuessedCorrectly())
                    .count() + 1;
            int points = pointsForRank(rank);
            player.setGuessedCorrectly(true);
            player.setGuessedAt(now);
            player.setGuessRank(rank);
            player.setCurrentRoundScore(points);
            player.setTotalScore(player.getTotalScore() + points);
            room.touch();

            boolean allGuessed = room.getPlayers().values().stream()
                    .filter(p -> !p.getId().equals(room.getArtistId()) && p.isOnline())
                    .allMatch(DrawGuessPlayer::isGuessedCorrectly);
            return new GuessResult(true, playerId, rank, points,
                    Math.max(0, now - room.getRoundStartedAt()), allGuessed);
        }
    }

    public RoundEnd endRound(String roomId, String reason, long now) {
        DrawGuessRoom room = manager.get(roomId);
        synchronized (room) {
            if (room.getState() != DrawGuessState.DRAWING) throw new IllegalArgumentException("当前不在绘画阶段");
            if (!"TIMEOUT".equals(reason) && !"ALL_GUESSED".equals(reason)
                    && !"ARTIST_DISCONNECTED".equals(reason)) throw new IllegalArgumentException("无效的本轮结束原因");
            if ("TIMEOUT".equals(reason) && now < room.getRoundDeadline()) {
                throw new IllegalArgumentException("绘画时间尚未结束");
            }
            if ("ALL_GUESSED".equals(reason)) {
                boolean allGuessed = room.getPlayers().values().stream()
                        .filter(p -> !p.getId().equals(room.getArtistId()) && p.isOnline())
                        .allMatch(DrawGuessPlayer::isGuessedCorrectly);
                if (!allGuessed) throw new IllegalArgumentException("仍有在线玩家尚未猜中");
            }
            int artistPoints = 0;
            DrawGuessPlayer artist = room.getPlayers().get(room.getArtistId());
            if (!"ARTIST_DISCONNECTED".equals(reason) && artist != null) {
                artistPoints = ARTIST_BONUS_POINTS;
                artist.setCurrentRoundScore(artist.getCurrentRoundScore() + artistPoints);
                artist.setTotalScore(artist.getTotalScore() + artistPoints);
            }

            List<PlayerRoundScore> scores = room.getPlayers().values().stream()
                    .map(p -> new PlayerRoundScore(p.getId(), p.getNickname(), p.getGuessRank(),
                            p.getCurrentRoundScore(), p.getTotalScore(), p.isGuessedCorrectly(),
                            p.getGuessedAt()))
                    .sorted(Comparator.comparingInt((PlayerRoundScore p) -> p.rank() == 0 ? Integer.MAX_VALUE : p.rank())
                            .thenComparing(PlayerRoundScore::playerId))
                    .toList();

            room.setState(room.getCurrentRound() == room.getTotalRounds()
                    ? DrawGuessState.GAME_OVER : DrawGuessState.ROUND_RESULT);
            room.setRoundDeadline(0);
            room.touch();
            return new RoundEnd(room.getCurrentRound(), reason, room.getCurrentAnswer(),
                    room.getArtistId(), artistPoints, scores, room.getState());
        }
    }

    public Map<String, Object> privateArtistOptions(String roomId, String playerId) {
        DrawGuessRoom room = manager.get(roomId);
        synchronized (room) {
            if (!playerId.equals(room.getArtistId()) || room.getState() != DrawGuessState.SELECTING) {
                throw new IllegalArgumentException("当前玩家不是选题画师");
            }
            return Map.of("round", room.getCurrentRound(), "options", room.getWordOptions(),
                    "expiresAt", room.getSelectionDeadline());
        }
    }

    private int pointsForRank(int rank) {
        if (rank <= 0) return 0;
        return rank <= RANK_POINTS.length ? RANK_POINTS[rank - 1] : REMAINING_RANK_POINTS;
    }

    private DrawGuessPlayer requirePlayer(DrawGuessRoom room, String playerId) {
        DrawGuessPlayer player = room.getPlayers().get(playerId);
        if (player == null) throw new IllegalArgumentException("玩家不在当前房间");
        return player;
    }

    private RoundSetup beginRound(DrawGuessRoom room, long now) {
        List<DrawGuessPlayer> eligible = room.getPlayers().values().stream().filter(DrawGuessPlayer::isOnline).toList();
        if (eligible.size() < DrawGuessManager.MIN_PLAYERS) throw new IllegalArgumentException("至少需要两名在线玩家");
        List<DrawGuessPlayer> candidates = eligible.stream()
                .filter(player -> !room.getArtists().contains(player.getId())).toList();
        if (candidates.isEmpty()) candidates = eligible;
        DrawGuessPlayer artist = candidates.get(RANDOM.nextInt(candidates.size()));
        room.setArtistId(artist.getId());
        room.getArtists().add(artist.getId());
        room.resetDrawing();
        room.setCurrentWordId(null);
        room.setCurrentAnswer(null);
        room.setWordOptions(words.drawOptions(room.getUsedWordIds()));
        room.setSelectionDeadline(now + SELECTION_TIMEOUT_MILLIS);
        room.setRoundStartedAt(0);
        room.setRoundDeadline(0);
        for (DrawGuessPlayer player : room.getPlayers().values()) {
            player.setGuessedCorrectly(false);
            player.setGuessedAt(0);
            player.setGuessRank(0);
            player.setCurrentRoundScore(0);
        }
        room.setState(DrawGuessState.SELECTING);
        room.touch();
        List<String> optionIds = room.getWordOptions().stream().map(word -> String.valueOf(word.id())).toList();
        return new RoundSetup(room.getCurrentRound(), artist.getId(), artist.getNickname(), optionIds,
                room.getSelectionDeadline());
    }

    private SelectedWord startDrawing(DrawGuessRoom room, DrawGuessWordRepository.Word word,
                                      long now, boolean autoSelected) {
        room.setCurrentWordId(String.valueOf(word.id()));
        room.setCurrentAnswer(word.answer());
        room.getUsedWordIds().add(word.id());
        room.setState(DrawGuessState.DRAWING);
        room.setSelectionDeadline(0);
        room.setRoundStartedAt(now);
        room.setRoundDeadline(now + ROUND_DURATION_MILLIS);
        room.touch();
        return new SelectedWord(room.getCurrentRound(), word.id(), word.category(), word.answer(),
                room.getRoundDeadline(), autoSelected);
    }

    private void requireHost(DrawGuessRoom room, String playerId) {
        if (playerId == null || !playerId.equals(room.getHostPlayerId())) throw new IllegalArgumentException("只有房主可以执行此操作");
    }
    private void requireState(DrawGuessRoom room, DrawGuessState expected) {
        if (room.getState() != expected) throw new IllegalArgumentException("当前游戏状态不允许此操作");
    }

    public record RoundSetup(int round, String artistId, String artistNickname, List<String> optionIds,
                             long selectionDeadline) {}
    public record SelectedWord(int round, int wordId, String category, String answer,
                               long roundDeadline, boolean autoSelected) {}
    public record GuessResult(boolean correct, String playerId, int rank, int points,
                              long elapsedMillis, boolean allGuessed) {}
    public record PlayerRoundScore(String playerId, String nickname, int rank, int roundPoints,
                                   int totalPoints, boolean guessedCorrectly, long guessedAt) {}
    public record RoundEnd(int round, String reason, String answer, String artistId, int artistPoints,
                           List<PlayerRoundScore> scores, DrawGuessState nextState) {}
}
