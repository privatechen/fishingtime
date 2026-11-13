package com.fishingtime.partygame.drawguess;

import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class DrawGuessReconnectService {
    private final DrawGuessManager manager;
    private final DrawGuessGameService game;
    private final DrawGuessDrawingService drawing;

    public DrawGuessReconnectService(DrawGuessManager manager, DrawGuessGameService game,
                                     DrawGuessDrawingService drawing) {
        this.manager = manager;
        this.game = game;
        this.drawing = drawing;
    }

    public DisconnectResult disconnect(WebSocketSession session, long now) {
        DrawGuessManager.DisconnectInfo info = manager.disconnect(session);
        if (info == null) return null;
        DrawGuessRoom room = info.room();
        DrawGuessGameService.RoundEnd ended = null;
        synchronized (room) {
            if (info.artist() && info.state() == DrawGuessState.DRAWING) {
                ended = game.endRound(room.getRoomId(), "ARTIST_DISCONNECTED", now);
            } else if (info.artist() && info.state() == DrawGuessState.SELECTING) {
                room.setState(room.getCurrentRound() >= room.getTotalRounds()
                        ? DrawGuessState.GAME_OVER : DrawGuessState.ROUND_RESULT);
                room.setSelectionDeadline(0);
                room.touch();
            }
        }
        return new DisconnectResult(room.getRoomId(), info.playerId(), info.artist(),
                manager.snapshot(room), ended);
    }

    public ReconnectResult reconnect(WebSocketSession session, String roomId, String playerId,
                                     String token, long now) {
        DrawGuessPlayer player = manager.reconnect(session, roomId, playerId, token);
        DrawGuessRoom room = manager.get(roomId);
        synchronized (room) {
            Map<String, Object> state = new LinkedHashMap<>(manager.snapshot(room));
            state.put("roundDeadline", room.getRoundDeadline());
            state.put("selectionDeadline", room.getSelectionDeadline());
            long remaining = room.getState() == DrawGuessState.DRAWING
                    ? room.getRoundDeadline() - now
                    : room.getState() == DrawGuessState.SELECTING ? room.getSelectionDeadline() - now : 0L;
            state.put("remainingMillis", Math.max(0L, remaining));
            state.put("guessedCorrectly", player.isGuessedCorrectly());
            state.put("guessRank", player.getGuessRank());
            state.put("currentRoundScore", player.getCurrentRoundScore());
            if (player.getId().equals(room.getArtistId()) && room.getCurrentAnswer() != null) {
                state.put("answer", room.getCurrentAnswer());
            }
            return new ReconnectResult(room.getRoomId(), player.getId(), state,
                    drawing.snapshot(roomId), player.getReconnectToken());
        }
    }

    public record DisconnectResult(String roomId, String playerId, boolean artist,
                                   Map<String, Object> roomSnapshot,
                                   DrawGuessGameService.RoundEnd roundEnd) {}
    public record ReconnectResult(String roomId, String playerId, Map<String, Object> gameState,
                                  DrawGuessDrawingService.DrawingSnapshot drawing,
                                  String reconnectToken) {}
}
