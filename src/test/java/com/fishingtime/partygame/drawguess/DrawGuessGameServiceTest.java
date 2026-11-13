package com.fishingtime.partygame.drawguess;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.WebSocketSession;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DrawGuessGameServiceTest {
    private DrawGuessManager manager;
    private DrawGuessGameService game;
    private DrawGuessRoom room;
    private String hostId;
    private String guestId;

    @BeforeEach
    void setUp() {
        DrawGuessWordRepository words = new DrawGuessWordRepository();
        manager = new DrawGuessManager(words);
        game = new DrawGuessGameService(manager, words);
        room = manager.create(session("host"), "房主");
        hostId = room.getHostPlayerId();
        DrawGuessRoom joined = manager.join(session("guest"), room.getRoomId(), "玩家");
        guestId = joined.getPlayers().keySet().stream().filter(id -> !id.equals(hostId)).findFirst().orElseThrow();
    }

    @Test
    void startsRoundAndOnlyArtistCanChooseWord() {
        var setup = game.startGame(room.getRoomId(), hostId, 1_000L);
        assertEquals(1, setup.round());
        assertEquals(DrawGuessState.SELECTING, room.getState());
        assertEquals(3, setup.optionIds().size());
        String nonArtist = setup.artistId().equals(hostId) ? guestId : hostId;
        assertThrows(IllegalArgumentException.class,
                () -> game.selectWord(room.getRoomId(), nonArtist,
                        Integer.parseInt(setup.optionIds().get(0)), 2_000L));
        var selected = game.selectWord(room.getRoomId(), setup.artistId(),
                Integer.parseInt(setup.optionIds().get(0)), 2_000L);
        assertEquals(DrawGuessState.DRAWING, room.getState());
        assertEquals(42_000L, selected.roundDeadline());
        assertEquals(2_000L, room.getRoundStartedAt());
    }

    @Test
    void selectionTimeoutAutomaticallyChoosesWord() {
        var setup = game.startGame(room.getRoomId(), hostId, 10_000L);
        var selected = game.processSelectionTimeout(room.getRoomId(), 25_000L);
        assertNotNull(selected);
        assertTrue(selected.autoSelected());
        assertEquals(65_000L, selected.roundDeadline());
        assertNull(game.processSelectionTimeout(room.getRoomId(), 30_000L));
    }

    @Test
    void nextRoundRequiresHostAndManualAction() {
        var setup = game.startGame(room.getRoomId(), hostId, 1_000L);
        game.selectWord(room.getRoomId(), setup.artistId(),
                Integer.parseInt(setup.optionIds().get(0)), 2_000L);
        game.endRound(room.getRoomId(), "TIMEOUT", 42_000L);
        assertEquals(DrawGuessState.ROUND_RESULT, room.getState());
        assertThrows(IllegalArgumentException.class, () -> game.nextRound(room.getRoomId(), guestId, 43_000L));
        var next = game.nextRound(room.getRoomId(), hostId, 50_000L);
        assertEquals(2, next.round());
        assertEquals(DrawGuessState.SELECTING, room.getState());
    }

    @Test
    void exactGuessAwardsRankPointsAndArtistBonus() {
        var setup = game.startGame(room.getRoomId(), hostId, 1_000L);
        var selected = game.selectWord(room.getRoomId(), setup.artistId(),
                Integer.parseInt(setup.optionIds().get(0)), 2_000L);
        String guesser = setup.artistId().equals(hostId) ? guestId : hostId;
        assertFalse(game.submitGuess(room.getRoomId(), guesser, selected.answer() + " ", 3_000L).correct());
        var correct = game.submitGuess(room.getRoomId(), guesser, selected.answer(), 4_000L);
        assertTrue(correct.correct());
        assertEquals(1, correct.rank());
        assertEquals(10, correct.points());
        assertTrue(correct.allGuessed());
        assertThrows(IllegalArgumentException.class,
                () -> game.submitGuess(room.getRoomId(), guesser, selected.answer(), 5_000L));
        var result = game.endRound(room.getRoomId(), "ALL_GUESSED", 5_000L);
        assertEquals(5, result.artistPoints());
        assertEquals(10, result.scores().stream().filter(s -> s.playerId().equals(guesser)).findFirst().orElseThrow().roundPoints());
    }

    @Test
    void artistDisconnectDoesNotReceiveBonus() {
        var setup = game.startGame(room.getRoomId(), hostId, 1_000L);
        game.selectWord(room.getRoomId(), setup.artistId(),
                Integer.parseInt(setup.optionIds().get(0)), 2_000L);
        var result = game.endRound(room.getRoomId(), "ARTIST_DISCONNECTED", 3_000L);
        assertEquals(0, result.artistPoints());
    }

    @Test
    void secondRoundEndMovesDirectlyToGameOver() {
        var first = game.startGame(room.getRoomId(), hostId, 1_000L);
        game.selectWord(room.getRoomId(), first.artistId(), Integer.parseInt(first.optionIds().get(0)), 2_000L);
        game.endRound(room.getRoomId(), "TIMEOUT", 42_000L);
        var second = game.nextRound(room.getRoomId(), hostId, 50_000L);
        game.selectWord(room.getRoomId(), second.artistId(), Integer.parseInt(second.optionIds().get(0)), 51_000L);
        var result = game.endRound(room.getRoomId(), "TIMEOUT", 91_000L);
        assertEquals(DrawGuessState.GAME_OVER, result.nextState());
        assertEquals(DrawGuessState.GAME_OVER, room.getState());
    }

    private WebSocketSession session(String id) {
        WebSocketSession session = mock(WebSocketSession.class);
        Map<String, Object> attributes = new HashMap<>();
        when(session.getId()).thenReturn(id);
        when(session.isOpen()).thenReturn(true);
        when(session.getAttributes()).thenReturn(attributes);
        return session;
    }
}
