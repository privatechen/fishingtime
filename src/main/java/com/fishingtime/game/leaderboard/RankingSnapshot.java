package com.fishingtime.game.leaderboard;

import java.util.List;
import java.util.Map;

/**
 * 排行榜内存快照（原子整体替换，避免查询线程读到半成品，PRD §20.5）
 */
public class RankingSnapshot {

    /** 已排序完整榜单（含名次） */
    private final List<RankingItem> rankingList;
    /** userId → rank */
    private final Map<Long, Integer> userRankingMap;
    /** TODAY 用当天日期（跨天校验）；ALL 为 null */
    private final String rankingDate;

    public RankingSnapshot(List<RankingItem> rankingList, Map<Long, Integer> userRankingMap, String rankingDate) {
        this.rankingList = rankingList;
        this.userRankingMap = userRankingMap;
        this.rankingDate = rankingDate;
    }

    public List<RankingItem> getRankingList() {
        return rankingList;
    }

    public Map<Long, Integer> getUserRankingMap() {
        return userRankingMap;
    }

    public String getRankingDate() {
        return rankingDate;
    }
}
