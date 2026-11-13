package com.fishingtime.partygame.drawguess;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * In-memory room aggregate. All mutable room operations are guarded by the
 * owning DrawGuessManager's synchronized methods in this first phase.
 */
public class DrawGuessRoom {
    private final String roomId;
    private final Map<String, DrawGuessPlayer> players = new LinkedHashMap<>();
    private String hostPlayerId;
    private DrawGuessState state = DrawGuessState.WAITING;
    private int currentRound;
    private final int totalRounds = 2;
    private String artistId;
    private String currentWordId;
    private long roundStartedAt;
    private long roundDeadline;
    private long updatedAt;

    public DrawGuessRoom(String roomId) {
        this.roomId = roomId;
        this.updatedAt = System.currentTimeMillis();
    }

    public String getRoomId() { return roomId; }
    public Map<String, DrawGuessPlayer> getPlayers() { return players; }
    public String getHostPlayerId() { return hostPlayerId; }
    public void setHostPlayerId(String hostPlayerId) { this.hostPlayerId = hostPlayerId; }
    public DrawGuessState getState() { return state; }
    public void setState(DrawGuessState state) { this.state = state; }
    public int getCurrentRound() { return currentRound; }
    public void setCurrentRound(int currentRound) { this.currentRound = currentRound; }
    public int getTotalRounds() { return totalRounds; }
    public String getArtistId() { return artistId; }
    public void setArtistId(String artistId) { this.artistId = artistId; }
    public String getCurrentWordId() { return currentWordId; }
    public void setCurrentWordId(String currentWordId) { this.currentWordId = currentWordId; }
    public long getRoundStartedAt() { return roundStartedAt; }
    public void setRoundStartedAt(long roundStartedAt) { this.roundStartedAt = roundStartedAt; }
    public long getRoundDeadline() { return roundDeadline; }
    public void setRoundDeadline(long roundDeadline) { this.roundDeadline = roundDeadline; }
    public long getUpdatedAt() { return updatedAt; }
    public void touch() { this.updatedAt = System.currentTimeMillis(); }
}
