package com.fishingtime.game.leaderboard;

/**
 * 排行榜条目（内存快照中的一行，已含名次）
 */
public class RankingItem {

    private final int rank;
    private final long userId;
    private final String nickname;
    private final int score;
    private final Integer secondaryScore;

    public RankingItem(int rank, long userId, String nickname, int score, Integer secondaryScore) {
        this.rank = rank;
        this.userId = userId;
        this.nickname = nickname;
        this.score = score;
        this.secondaryScore = secondaryScore;
    }

    public int getRank() {
        return rank;
    }

    public long getUserId() {
        return userId;
    }

    public String getNickname() {
        return nickname;
    }

    public int getScore() {
        return score;
    }

    public Integer getSecondaryScore() {
        return secondaryScore;
    }
}
