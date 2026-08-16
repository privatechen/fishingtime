package com.fishingtime.game.leaderboard;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 各游戏排行榜排序配置（不硬编码在某款游戏接口里，PRD §8/§98）
 *
 * direction: 'desc' 分数型（越高越前）/ 'asc' 耗时型（越低越前，如颜色猎手）
 * useSecondary: 排序是否纳入次级指标（鱼群突围：同清空池数再比放生数）
 */
@Component
public class GameRankingConfig {

    private static final class Spec {
        private final String direction;
        private final boolean useSecondary;

        Spec(String direction, boolean useSecondary) {
            this.direction = direction;
            this.useSecondary = useSecondary;
        }
    }

    private static final Map<String, Spec> SPECS = new HashMap<>();
    static {
        SPECS.put("2048", new Spec("desc", false));
        SPECS.put("color-focus", new Spec("desc", false));
        SPECS.put("direction-trap", new Spec("desc", false));
        SPECS.put("color-hunter", new Spec("asc", false));
        SPECS.put("fish-breakout", new Spec("desc", true));
        SPECS.put("extreme-fishing", new Spec("desc", false));
        // 《细节》：答对数降序为主，用时升序为次级，走独立窗口函数查询
        SPECS.put("detail", new Spec("desc", true));
    }

    private static final Spec DEFAULT = new Spec("desc", false);

    public boolean isKnown(String gameCode) {
        return SPECS.containsKey(gameCode);
    }

    public String direction(String gameCode) {
        return SPECS.getOrDefault(gameCode, DEFAULT).direction;
    }

    public boolean useSecondary(String gameCode) {
        return SPECS.getOrDefault(gameCode, DEFAULT).useSecondary;
    }
}
