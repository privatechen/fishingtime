package com.fishingtime.partygame.drawguess;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.WebSocketSession;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DrawGuessReconnectServiceTest {
    private DrawGuessManager manager;
    private DrawGuessGameService game;
    private DrawGuessDrawingService drawing;
    private DrawGuessReconnectService reconnect;
    private DrawGuessRoom room;
    private String hostId;
    private String guestId;
    private String guestToken;
    private WebSocketSession guestSession;

    @BeforeEach
    void setUp() {
        DrawGuessWordRepository words = new DrawGuessWordRepository();
        manager = new DrawGuessManager(words);
        game = new DrawGuessGameService(manager, words);
        drawing = new DrawGuessDrawingService(manager);
        reconnect = new DrawGuessReconnectService(manager, game, drawing);
        room = manager.create(session("host"), "房主");
        hostId = room.getHostPlayerId();
        guestSession = session("guest");
        manager.join(guestSession, room.getRoomId(), "玩家");
        guestId = room.getPlayers().keySet().stream().filter(id -> !id.equals(hostId)).findFirst().orElseThrow();
        guestToken = room.getPlayers().get(guestId).getReconnectToken();
    }

    @Test
    void disconnectedGuesserIsRetainedAndCanReconnectWithSameIdentity() {
        reconnect.disconnect(guestSession, 1_000L);
        assertTrue(room.getPlayers().containsKey(guestId));
        assertFalse(room.getPlayers().get(guestId).isOnline());
        WebSocketSession fresh = session("guest-new");
        var result = reconnect.reconnect(fresh, room.getRoomId(), guestId, guestToken, 2_000L);
        assertEquals(guestId, result.playerId());
        assertEquals(room.getRoomId(), fresh.getAttributes().get("drawGuessRoomId"));
        assertTrue(room.getPlayers().get(guestId).isOnline());
    }

    @Test
    void invalidTokenCannotReclaimPlayer() {
        reconnect.disconnect(guestSession, 1_000L);
        assertThrows(IllegalArgumentException.class,
                () -> reconnect.reconnect(session("intruder"), room.getRoomId(), guestId, "wrong-token", 2_000L));
    }

    @Test
    void artistDisconnectEndsDrawingRoundWithoutArtistBonus() {
        var setup = game.startGame(room.getRoomId(), hostId, 1_000L);
        game.selectWord(room.getRoomId(), setup.artistId(), Integer.parseInt(setup.optionIds().get(0)), 2_000L);
        WebSocketSession artistSession = room.getPlayers().get(setup.artistId()).getSession();
        var result = reconnect.disconnect(artistSession, 3_000L);
        assertTrue(result.artist());
        assertEquals("ARTIST_DISCONNECTED", result.roundEnd().reason());
        assertEquals(0, result.roundEnd().artistPoints());
    }

    @Test
    void reconnectRestoresRemainingTimeAndPrivateAnswerForArtist() {
        var setup = game.startGame(room.getRoomId(), hostId, 1_000L);
        game.selectWord(room.getRoomId(), setup.artistId(), Integer.parseInt(setup.optionIds().get(0)), 2_000L);
        DrawGuessPlayer artist = room.getPlayers().get(setup.artistId());
        manager.disconnect(artist.getSession());
        WebSocketSession fresh = session("artist-new");
        var result = reconnect.reconnect(fresh, room.getRoomId(), artist.getId(),
                artist.getReconnectToken(), 10_000L);
        assertEquals(32_000L, ((Number) result.gameState().get("remainingMillis")).longValue());
        assertEquals(room.getCurrentRound(), result.drawing().round());
        assertTrue(result.gameState().containsKey("answer"));
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
