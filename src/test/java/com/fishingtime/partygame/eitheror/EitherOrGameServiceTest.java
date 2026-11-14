package com.fishingtime.partygame.eitheror;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.WebSocketSession;

import java.util.HashMap;
import java.util.Map;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EitherOrGameServiceTest {
    private EitherOrManager manager;
    private EitherOrQuestionRepository questions;
    private EitherOrGameService game;
    private EitherOrManager.Room room;
    private String hostId;
    private String playerB;
    private String playerC;

    @BeforeEach
    void setUp() {
        manager = new EitherOrManager();
        questions = new EitherOrQuestionRepository();
        game = new EitherOrGameService(manager, questions);
        room = manager.create(session("host"), "房主");
        hostId = room.hostPlayerId;
        manager.join(session("b"), room.roomId, "玩家B");
        manager.join(session("c"), room.roomId, "玩家C");
        playerB = room.players.values().stream().filter(p -> !p.id.equals(hostId))
                .map(p -> p.id).findFirst().orElseThrow();
        playerC = room.players.values().stream().filter(p -> !p.id.equals(hostId) && !p.id.equals(playerB))
                .map(p -> p.id).findFirst().orElseThrow();
    }

    @Test
    void questionBankContainsTwoHundredAndGameDrawsTenUniqueQuestions() {
        assertEquals(200, questions.size());
        var started = game.start(room.roomId, hostId, 1_000L);
        assertEquals(1, started.round());
        assertEquals(10, room.questions.size());
        assertEquals(10, room.questions.stream().map(EitherOrManager.Question::id).distinct().count());
        assertEquals(EitherOrManager.State.VOTING, room.state);
        assertEquals(11_000L, started.deadline());
    }

    @Test
    void minorityGetsOnePointAndAllOnlineVotesFinishTheRound() {
        game.start(room.roomId, hostId, 1_000L);
        assertNull(game.submit(room.roomId, hostId, "A", 2_000L).result());
        assertNull(game.submit(room.roomId, playerB, "B", 3_000L).result());
        var last = game.submit(room.roomId, playerC, "B", 4_000L);
        assertNotNull(last.result());
        assertEquals(1, last.result().votesA());
        assertEquals(2, last.result().votesB());
        assertEquals("A", last.result().minority());
        assertEquals(1, room.players.get(hostId).score);
        assertEquals(0, room.players.get(playerB).score);
        assertEquals(EitherOrManager.State.RESULT, room.state);
        assertThrows(IllegalArgumentException.class, () -> game.submit(room.roomId, hostId, "B", 5_000L));
    }

    @Test
    void tieDoesNotAwardPointsAndHostControlsNextQuestion() {
        game.start(room.roomId, hostId, 1_000L);
        game.submit(room.roomId, hostId, "A", 2_000L);
        game.submit(room.roomId, playerB, "B", 3_000L);
        var result = game.processTimers(11_000L).get(0);
        assertEquals("TIE", result.minority());
        assertEquals(0, room.players.get(hostId).score);
        assertEquals(0, room.players.get(playerB).score);
        assertThrows(IllegalArgumentException.class, () -> game.next(room.roomId, playerB, 4_000L));
        var next = game.next(room.roomId, hostId, 4_000L);
        assertEquals(2, next.round());
        assertEquals(EitherOrManager.State.VOTING, room.state);
    }

    @Test
    void voteTimeoutEndsRoundAndOnlySubmittedVotesCount() {
        game.start(room.roomId, hostId, 1_000L);
        game.submit(room.roomId, hostId, "A", 2_000L);
        List<EitherOrGameService.RoundResult> results = game.processTimers(11_000L);
        assertEquals(1, results.size());
        assertEquals(1, results.get(0).votesA());
        assertEquals(0, results.get(0).votesB());
        assertEquals("TIE", results.get(0).minority());
    }

    private WebSocketSession session(String id) {
        WebSocketSession session = mock(WebSocketSession.class);
        Map<String,Object> attributes = new HashMap<>();
        when(session.getId()).thenReturn(id);
        when(session.isOpen()).thenReturn(true);
        when(session.getAttributes()).thenReturn(attributes);
        return session;
    }
}
