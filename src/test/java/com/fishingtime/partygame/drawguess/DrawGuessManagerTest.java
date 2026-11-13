package com.fishingtime.partygame.drawguess;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.WebSocketSession;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DrawGuessManagerTest {
    private DrawGuessManager manager;

    @BeforeEach
    void setUp() {
        manager = new DrawGuessManager(new DrawGuessWordRepository());
    }

    @Test
    void createsRoomAndBindsHostSession() {
        WebSocketSession host = session("host");
        DrawGuessRoom room = manager.create(host, "小明");

        assertEquals(6, room.getRoomId().length());
        assertEquals(1, room.getPlayers().size());
        assertEquals(DrawGuessState.WAITING, room.getState());
        assertEquals(room.getRoomId(), host.getAttributes().get("drawGuessRoomId"));
        assertEquals(room.getHostPlayerId(), host.getAttributes().get("drawGuessPlayerId"));
        assertEquals("小明", room.getPlayers().get(room.getHostPlayerId()).getNickname());
    }

    @Test
    void joinsRoomAndEnforcesEightPlayerLimit() {
        DrawGuessRoom room = manager.create(session("p1"), "玩家1");
        for (int i = 2; i <= 8; i++) {
            manager.join(session("p" + i), room.getRoomId(), "玩家" + i);
        }
        assertEquals(8, room.getPlayers().size());
        assertThrows(IllegalArgumentException.class,
                () -> manager.join(session("p9"), room.getRoomId(), "玩家9"));
    }

    @Test
    void leaveTransfersHostAndRemovesEmptyRoom() {
        WebSocketSession host = session("host");
        WebSocketSession guest = session("guest");
        DrawGuessRoom room = manager.create(host, "房主");
        manager.join(guest, room.getRoomId(), "玩家");

        manager.leave(host);
        assertEquals(guest.getAttributes().get("drawGuessPlayerId"), room.getHostPlayerId());
        assertEquals(1, room.getPlayers().size());

        manager.leave(guest);
        assertThrows(IllegalArgumentException.class, () -> manager.get(room.getRoomId()));
    }

    @Test
    void wordBankContainsExactlyTwoHundredUniqueAnswers() {
        DrawGuessWordRepository repository = new DrawGuessWordRepository();
        assertEquals(200, repository.size());
        assertEquals(200, repository.findAll().stream().map(DrawGuessWordRepository.Word::answer).distinct().count());
        assertEquals(3, repository.drawOptions().size());
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
