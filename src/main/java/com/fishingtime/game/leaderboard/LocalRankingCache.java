package com.fishingtime.game.leaderboard;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 排行榜本地内存缓存（PRD §20）
 *
 * Key = gameCode + period（TODAY / ALL），value 为完整 RankingSnapshot。
 * 排行榜 GET 默认 Cache First：命中直接返回，不查 MySQL。
 * 更新采用 put 整体替换引用（原子），保证查询线程始终读到完整版本。
 */
@Component
public class LocalRankingCache {

    private final ConcurrentHashMap<String, RankingSnapshot> cache = new ConcurrentHashMap<>();

    private String key(String gameCode, String period) {
        return gameCode + ":" + period;
    }

    public RankingSnapshot get(String gameCode, String period) {
        return cache.get(key(gameCode, period));
    }

    public void put(String gameCode, String period, RankingSnapshot snapshot) {
        cache.put(key(gameCode, period), snapshot);
    }

    /** 排名真实变化或 TODAY 跨天时移除，下次 GET 从 MySQL 重建 */
    public void invalidate(String gameCode, String period) {
        cache.remove(key(gameCode, period));
    }
}
