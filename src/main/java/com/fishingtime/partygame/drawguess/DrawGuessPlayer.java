package com.fishingtime.partygame.drawguess;

import org.springframework.web.socket.WebSocketSession;

/**
 * A player participating in one Draw & Guess room.
 * Reconnection behavior is implemented in a later phase; the token is
 * generated now so the identity model does not need to change later.
 */
public class DrawGuessPlayer {
    private final String id;
    private final String nickname;
    private final String reconnectToken;
    private WebSocketSession session;
    private boolean online;
    private int totalScore;
    private int currentRoundScore;
    private boolean guessedCorrectly;
    private long guessedAt;
    private int guessRank;

    public DrawGuessPlayer(String id, String nickname, String reconnectToken, WebSocketSession session) {
        this.id = id;
        this.nickname = nickname;
        this.reconnectToken = reconnectToken;
        this.session = session;
        this.online = session != null && session.isOpen();
    }

    public String getId() { return id; }
    public String getNickname() { return nickname; }
    public String getReconnectToken() { return reconnectToken; }
    public WebSocketSession getSession() { return session; }
    public void setSession(WebSocketSession session) { this.session = session; this.online = session != null && session.isOpen(); }
    public boolean isOnline() { return online && session != null && session.isOpen(); }
    public void setOnline(boolean online) { this.online = online; }
    public int getTotalScore() { return totalScore; }
    public void setTotalScore(int totalScore) { this.totalScore = totalScore; }
    public int getCurrentRoundScore() { return currentRoundScore; }
    public void setCurrentRoundScore(int currentRoundScore) { this.currentRoundScore = currentRoundScore; }
    public boolean isGuessedCorrectly() { return guessedCorrectly; }
    public void setGuessedCorrectly(boolean guessedCorrectly) { this.guessedCorrectly = guessedCorrectly; }
    public long getGuessedAt() { return guessedAt; }
    public void setGuessedAt(long guessedAt) { this.guessedAt = guessedAt; }
    public int getGuessRank() { return guessRank; }
    public void setGuessRank(int guessRank) { this.guessRank = guessRank; }
}
