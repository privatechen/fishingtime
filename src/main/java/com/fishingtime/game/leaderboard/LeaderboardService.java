package com.fishingtime.game.leaderboard;

import com.fishingtime.game.mapper.ColorFocusScoreMapper;
import com.fishingtime.game.mapper.ColorHunterScoreMapper;
import com.fishingtime.game.mapper.DirectionTrapScoreMapper;
import com.fishingtime.game.mapper.ExtremeFishingScoreMapper;
import com.fishingtime.game.mapper.FishBreakoutScoreMapper;
import com.fishingtime.game.mapper.Game2048ScoreMapper;
import com.fishingtime.game.mapper.GameScoreMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 排行榜服务（PRD §17/§20）
 *
 * - TODAY / ALL 统一从 game_score 聚合（TODAY 按北京时间当天，ALL 全量）
 * - Cache First：命中 LocalRankingCache 直接返回，不查 MySQL
 * - TODAY 跨天失效；分页基于内存快照切片
 * - myRank 从快照的 userRankingMap 取（用户不在 Top 范围内也能知道真实名次）
 */
@Service
@RequiredArgsConstructor
public class LeaderboardService {

    private static final ZoneId CN = ZoneId.of("Asia/Shanghai");
    private static final String MIN_DATE = "1970-01-01 00:00:00";
    private static final String MAX_DATE = "9999-12-31 23:59:59";

    private final GameScoreMapper gameScoreMapper;
    private final LocalRankingCache cache;
    private final GameRankingConfig config;
    private final Game2048ScoreMapper game2048ScoreMapper;
    private final ColorFocusScoreMapper colorFocusScoreMapper;
    private final DirectionTrapScoreMapper directionTrapScoreMapper;
    private final ColorHunterScoreMapper colorHunterScoreMapper;
    private final FishBreakoutScoreMapper fishBreakoutScoreMapper;
    private final ExtremeFishingScoreMapper extremeFishingScoreMapper;

    public LeaderboardDTO getLeaderboard(String gameCode, String period, int page, int pageSize, Long userId) {
        if (!config.isKnown(gameCode)) {
            throw new IllegalArgumentException("未知游戏: " + gameCode);
        }
        String direction = config.direction(gameCode);
        boolean useSecondary = config.useSecondary(gameCode);

        RankingSnapshot snap = getSnapshot(gameCode, period, direction, useSecondary);

        List<RankingItem> all = snap.getRankingList();
        int total = all.size();
        int from = Math.min(Math.max(page - 1, 0) * pageSize, total);
        int to = Math.min(from + pageSize, total);

        List<LeaderboardDTO.Item> items = new ArrayList<>();
        for (int i = from; i < to; i++) {
            RankingItem item = all.get(i);
            LeaderboardDTO.Item dto = new LeaderboardDTO.Item();
            dto.setRank(item.getRank());
            dto.setNickname(item.getNickname());
            dto.setScore(item.getScore());
            dto.setSecondaryScore(item.getSecondaryScore());
            dto.setMe(userId != null && item.getUserId() == userId);
            items.add(dto);
        }

        LeaderboardDTO.MyRank myRank = null;
        if (userId != null) {
            Integer rank = snap.getUserRankingMap().get(userId);
            if (rank != null) {
                RankingItem mine = all.get(rank - 1);
                myRank = new LeaderboardDTO.MyRank();
                myRank.setRank(rank);
                myRank.setScore(mine.getScore());
                myRank.setSecondaryScore(mine.getSecondaryScore());
            }
        }

        LeaderboardDTO dto = new LeaderboardDTO();
        dto.setPeriod(period);
        dto.setTimezone("Asia/Shanghai");
        dto.setItems(items);
        dto.setMyRank(myRank);
        dto.setTotal(total);
        return dto;
    }

    private RankingSnapshot getSnapshot(String gameCode, String period, String direction, boolean useSecondary) {
        RankingSnapshot snap = cache.get(gameCode, period);
        String today = LocalDate.now(CN).toString();
        // TODAY 跨天失效（同时校验 rankingDate 兜底）
        if (snap != null && "TODAY".equals(period) && !today.equals(snap.getRankingDate())) {
            snap = null;
        }
        if (snap != null) return snap;

        snap = buildSnapshot(gameCode, period, direction, useSecondary, today);
        cache.put(gameCode, period, snap);
        return snap;
    }

    private RankingSnapshot buildSnapshot(String gameCode, String period, String direction, boolean useSecondary, String today) {
        String start;
        String end;
        if ("TODAY".equals(period)) {
            start = today + " 00:00:00";
            end = today + " 23:59:59";
        } else {
            start = MIN_DATE;
            end = MAX_DATE;
        }

        // TODAY 从 game_score（每局日志）按当天聚合；ALL 走各游戏 best 表（历史最佳，PRD §7/§16）
        List<Map<String, Object>> rows;
        if ("TODAY".equals(period)) {
            rows = gameScoreMapper.selectRankByRange(gameCode, start, end, direction, useSecondary);
        } else {
            rows = queryAllRank(gameCode);
        }
        List<RankingItem> list = new ArrayList<>(rows.size());
        Map<Long, Integer> userMap = new HashMap<>();
        int rank = 1;
        for (Map<String, Object> row : rows) {
            Long userId = ((Number) row.get("userId")).longValue();
            String nickname = row.get("nickname") != null ? row.get("nickname").toString() : "匿名用户";
            int score = ((Number) row.get("score")).intValue();
            Object sec = row.get("secondaryScore");
            Integer secondary = sec != null ? ((Number) sec).intValue() : null;
            list.add(new RankingItem(rank, userId, nickname, score, secondary));
            userMap.put(userId, rank);
            rank++;
        }
        return new RankingSnapshot(list, userMap, "TODAY".equals(period) ? today : null);
    }

    /** 总榜数据源：各游戏 best 表全量（已按该游戏排序规则排好） */
    private List<Map<String, Object>> queryAllRank(String gameCode) {
        switch (gameCode) {
            case "2048":
                return game2048ScoreMapper.selectAllRank();
            case "color-focus":
                return colorFocusScoreMapper.selectAllRank();
            case "direction-trap":
                return directionTrapScoreMapper.selectAllRank();
            case "color-hunter":
                return colorHunterScoreMapper.selectAllRank();
            case "fish-breakout":
                return fishBreakoutScoreMapper.selectAllRank();
            case "extreme-fishing":
                return extremeFishingScoreMapper.selectAllRank();
            default:
                throw new IllegalArgumentException("未知游戏: " + gameCode);
        }
    }
}
