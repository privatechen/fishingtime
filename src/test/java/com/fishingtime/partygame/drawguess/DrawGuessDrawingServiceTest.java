package com.fishingtime.partygame.drawguess;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.WebSocketSession;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DrawGuessDrawingServiceTest {
    private DrawGuessManager manager;
    private DrawGuessDrawingService drawing;
    private DrawGuessRoom room;
    private String artistId;
    private String guestId;

    @BeforeEach
    void setUp() {
        manager = new DrawGuessManager(new DrawGuessWordRepository());
        drawing = new DrawGuessDrawingService(manager);
        room = manager.create(session("host"), "房主");
        String hostId = room.getHostPlayerId();
        manager.join(session("guest"), room.getRoomId(), "玩家");
        artistId = hostId;
        guestId = room.getPlayers().keySet().stream().filter(id -> !id.equals(hostId)).findFirst().orElseThrow();
        room.setArtistId(artistId);
        room.setState(DrawGuessState.DRAWING);
        room.setRoundDeadline(100_000L);
    }

    @Test
    void acceptsIncrementalStrokeAndSnapshot() {
        var started = drawing.begin(room.getRoomId(), artistId, "#8844CC", 4f, 1_000L);
        var accepted = drawing.append(room.getRoomId(), artistId, started.strokeId(), 0,
                List.of(new DrawPoint(0.1f, 0.2f), new DrawPoint(0.3f, 0.4f)), 1_050L);
        drawing.end(room.getRoomId(), artistId, started.strokeId(), 1_100L);
        var snapshot = drawing.snapshot(room.getRoomId());
        assertEquals(2, accepted.points().size());
        assertEquals(2, snapshot.totalPoints());
        assertEquals(1, snapshot.strokes().size());
        assertTrue(snapshot.strokes().get(0).ended());
    }

    @Test
    void rejectsNonArtistAndInvalidCoordinates() {
        assertThrows(IllegalArgumentException.class,
                () -> drawing.begin(room.getRoomId(), guestId, "#000000", 3f, 1_000L));
        assertThrows(IllegalArgumentException.class, () -> new DrawPoint(1.1f, 0.5f));
        assertThrows(IllegalArgumentException.class,
                () -> drawing.begin(room.getRoomId(), artistId, "red", 3f, 1_000L));
    }

    @Test
    void enforcesSequenceAndPacketLimits() {
        var stroke = drawing.begin(room.getRoomId(), artistId, "#000000", 3f, 1_000L);
        assertThrows(IllegalArgumentException.class,
                () -> drawing.append(room.getRoomId(), artistId, stroke.strokeId(), 1,
                        List.of(new DrawPoint(0.2f, 0.2f)), 1_050L));
        assertThrows(IllegalArgumentException.class,
                () -> drawing.append(room.getRoomId(), artistId, stroke.strokeId(), 0,
                        java.util.Collections.nCopies(65, new DrawPoint(0.2f, 0.2f)), 1_050L));
    }

    @Test
    void supportsUndoAndClear() {
        var first = drawing.begin(room.getRoomId(), artistId, "#000000", 3f, 1_000L);
        drawing.append(room.getRoomId(), artistId, first.strokeId(), 0,
                List.of(new DrawPoint(0.1f, 0.1f)), 1_050L);
        drawing.end(room.getRoomId(), artistId, first.strokeId(), 1_100L);
        assertTrue(drawing.undo(room.getRoomId(), artistId, 1_200L).changed());
        assertEquals(0, drawing.snapshot(room.getRoomId()).totalPoints());

        var second = drawing.begin(room.getRoomId(), artistId, "#FFFFFF", 5f, 1_300L);
        drawing.append(room.getRoomId(), artistId, second.strokeId(), 0,
                List.of(new DrawPoint(0.5f, 0.5f)), 1_350L);
        assertEquals(1, drawing.clear(room.getRoomId(), artistId, 1_400L).removedStrokes());
        assertTrue(drawing.snapshot(room.getRoomId()).strokes().isEmpty());
    }

    @Test
    void rejectsDrawingAfterDeadline() {
        assertThrows(IllegalArgumentException.class,
                () -> drawing.begin(room.getRoomId(), artistId, "#000000", 3f, 100_000L));
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
