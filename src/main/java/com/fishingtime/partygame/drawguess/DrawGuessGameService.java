package com.fishingtime.partygame.drawguess;

import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Server-authoritative game state transitions. Network delivery is kept out
 * of this service so transitions can be tested deterministically.
 */
@Service
public class DrawGuessGameService {
    public static final long SELECTION_TIMEOUT_MILLIS = 15_000L;
    public static final long ROUND_DURATION_MILLIS = 40_000L;
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

    public RoundSetup nextRound(String roomId, String playerId, long now) {
        DrawGuessRoom room = manager.get(roomId);
        synchronized (room) {
            requireHost(room, playerId);
            requireState(room, DrawGuessState.ROUND_RESULT);
            if (room.getCurrentRound() >= room.getTotalRounds()) {
                throw new IllegalArgumentException("本局已经结束");
            }
            room.setCurrentRound(room.getCurrentRound() + 1);
            for (DrawGuessPlayer player : room.getPlayers().values()) {
                player.setGuessedCorrectly(false);
                player.setGuessedAt(0);
                player.setGuessRank(0);
                player.setCurrentRoundScore(0);
            }
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

    /**
     * Called by the shared scheduler. Returns the transition if a selection
     * deadline expired; otherwise returns null.
     */
    public SelectedWord processSelectionTimeout(String roomId, long now) {
        DrawGuessRoom room = manager.get(roomId);
        synchronized (room) {
            if (room.getState() != DrawGuessState.SELECTING || now < room.getSelectionDeadline()) return null;
            DrawGuessWordRepository.Word selected = room.getWordOptions().get(RANDOM.nextInt(room.getWordOptions().size()));
            return startDrawing(room, selected, now, true);
        }
    }

    public RoundEnd endRound(String roomId, String reason, long now) {
        DrawGuessRoom room = manager.get(roomId);
        synchronized (room) {
            if (room.getState() != DrawGuessState.DRAWING) throw new IllegalArgumentException("当前不在绘画阶段");
            if (!"TIMEOUT".equals(reason) && !"ALL_GUESSED".equals(reason)
                    && !"ARTIST_DISCONNECTED".equals(reason)) {
                throw new IllegalArgumentException("无效的本轮结束原因");
            }
            if ("TIMEOUT".equals(reason) && now < room.getRoundDeadline()) {
                throw new IllegalArgumentException("绘画时间尚未结束");
            }
            room.setState(room.getCurrentRound() == room.getTotalRounds()
                    ? DrawGuessState.GAME_OVER : DrawGuessState.ROUND_RESULT);
            room.setRoundDeadline(0);
            room.touch();
            return new RoundEnd(room.getCurrentRound(), reason, room.getCurrentAnswer(),
                    room.getArtistId(), room.getState());
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

    private RoundSetup beginRound(DrawGuessRoom room, long now) {
        List<DrawGuessPlayer> eligible = room.getPlayers().values().stream()
                .filter(DrawGuessPlayer::isOnline)
                .toList();
        if (eligible.size() < DrawGuessManager.MIN_PLAYERS) throw new IllegalArgumentException("至少需要两名在线玩家");
        List<DrawGuessPlayer> candidates = eligible.stream()
                .filter(player -> !room.getArtists().contains(player.getId())).toList();
        if (candidates.isEmpty()) candidates = eligible;
        DrawGuessPlayer artist = candidates.get(RANDOM.nextInt(candidates.size()));
        room.setArtistId(artist.getId());
        room.getArtists().add(artist.getId());
        room.setCurrentWordId(null);
        room.setCurrentAnswer(null);
        room.setWordOptions(words.drawOptions(room.getUsedWordIds()));
        room.setSelectionDeadline(now + SELECTION_TIMEOUT_MILLIS);
        room.setRoundStartedAt(0);
        room.setRoundDeadline(0);
        room.setState(DrawGuessState.SELECTING);
        room.touch();
        List<String> optionIds = room.getWordOptions().stream().map(word -> String.valueOf(word.id())).toList();
        return new RoundSetup(room.getCurrentRound(), artist.getId(), artist.getNickname(),
                optionIds, room.getSelectionDeadline());
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
        return new SelectedWord(room.getCurrentRound(), word.id(), word.category(),
                word.answer(), room.getRoundDeadline(), autoSelected);
    }

    private void requireHost(DrawGuessRoom room, String playerId) {
        if (playerId == null || !playerId.equals(room.getHostPlayerId())) {
            throw new IllegalArgumentException("只有房主可以执行此操作");
        }
    }

    private void requireState(DrawGuessRoom room, DrawGuessState expected) {
        if (room.getState() != expected) throw new IllegalArgumentException("当前游戏状态不允许此操作");
    }

    public record RoundSetup(int round, String artistId, String artistNickname,
                             List<String> optionIds, long selectionDeadline) {}
    public record SelectedWord(int round, int wordId, String category, String answer,
                               long roundDeadline, boolean autoSelected) {}
    public record RoundEnd(int round, String reason, String answer, String artistId,
                           DrawGuessState nextState) {}
}
