package com.fishingtime.partygame.drawguess;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DrawGuessRoom {
    private final String roomId;
    private final Map<String, DrawGuessPlayer> players = new LinkedHashMap<>();
    private final Set<String> artists = new LinkedHashSet<>();
    private final Set<Integer> usedWordIds = new LinkedHashSet<>();
    private List<DrawGuessWordRepository.Word> wordOptions = new ArrayList<>();
    private String hostPlayerId;
    private DrawGuessState state = DrawGuessState.WAITING;
    private int currentRound;
    private final int totalRounds = 2;
    private String artistId;
    private String currentWordId;
    private String currentAnswer;
    private long selectionDeadline;
    private long roundStartedAt;
    private long roundDeadline;
    private long updatedAt;

    public DrawGuessRoom(String roomId) { this.roomId = roomId; this.updatedAt = System.currentTimeMillis(); }
    public String getRoomId() { return roomId; }
    public Map<String, DrawGuessPlayer> getPlayers() { return players; }
    public Set<String> getArtists() { return artists; }
    public Set<Integer> getUsedWordIds() { return usedWordIds; }
    public List<DrawGuessWordRepository.Word> getWordOptions() { return List.copyOf(wordOptions); }
    public void setWordOptions(List<DrawGuessWordRepository.Word> options) { this.wordOptions = new ArrayList<>(options); }
    public String getHostPlayerId() { return hostPlayerId; }
    public void setHostPlayerId(String value) { hostPlayerId = value; }
    public DrawGuessState getState() { return state; }
    public void setState(DrawGuessState value) { state = value; }
    public int getCurrentRound() { return currentRound; }
    public void setCurrentRound(int value) { currentRound = value; }
    public int getTotalRounds() { return totalRounds; }
    public String getArtistId() { return artistId; }
    public void setArtistId(String value) { artistId = value; }
    public String getCurrentWordId() { return currentWordId; }
    public void setCurrentWordId(String value) { currentWordId = value; }
    public String getCurrentAnswer() { return currentAnswer; }
    public void setCurrentAnswer(String value) { currentAnswer = value; }
    public long getSelectionDeadline() { return selectionDeadline; }
    public void setSelectionDeadline(long value) { selectionDeadline = value; }
    public long getRoundStartedAt() { return roundStartedAt; }
    public void setRoundStartedAt(long value) { roundStartedAt = value; }
    public long getRoundDeadline() { return roundDeadline; }
    public void setRoundDeadline(long value) { roundDeadline = value; }
    public long getUpdatedAt() { return updatedAt; }
    public void touch() { updatedAt = System.currentTimeMillis(); }
}
