package com.fishingtime.game.leaderboard;

import com.fishingtime.game.mapper.ColorFocusScoreMapper;
import com.fishingtime.game.mapper.ColorHunterScoreMapper;
import com.fishingtime.game.mapper.DirectionTrapScoreMapper;
import com.fishingtime.game.mapper.DontFillScoreMapper;
import com.fishingtime.game.mapper.ExtremeFishingScoreMapper;
import com.fishingtime.game.mapper.DetailScoreMapper;
import com.fishingtime.game.mapper.FishBreakoutScoreMapper;
import com.fishingtime.game.mapper.Game2048ScoreMapper;
import com.fishingtime.game.mapper.GameScoreMapper;
import com.fishingtime.game.mapper.StackTowerScoreMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    private final DetailScoreMapper detailScoreMapper;
    private final DontFillScoreMapper dontFillScoreMapper;
    private final StackTowerScoreMapper stackTowerScoreMapper;

    public LeaderboardDTO getLeaderboard(String gameCode, String period, int page, int pageSize, Long userId) {
        if (!config.isKnown(gameCode)) throw new IllegalArgumentException("未知游戏: " + gameCode);
        String direction = config.direction(gameCode);
        boolean useSecondary = config.useSecondary(gameCode);
        RankingSnapshot snap = getSnapshot(gameCode, period, direction, useSecondary);

        List<RankingItem> all = snap.getRankingList();
        int total = all.size();
        int to = Math.min(20, total);
        List<LeaderboardDTO.Item> items = new ArrayList<>();
        for (int i = 0; i < to; i++) {
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
        if (snap != null && "TODAY".equals(period) && !today.equals(snap.getRankingDate())) snap = null;
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

        List<Map<String, Object>> rows;
        if ("TODAY".equals(period)) {
            if ("detail".equals(gameCode) || "dont-fill".equals(gameCode)) {
                rows = gameScoreMapper.selectBestGameRankByRange(gameCode, start, end);
            } else if ("stack-tower".equals(gameCode)) {
                rows = gameScoreMapper.selectBestGameRankDescSecondaryByRange(gameCode, start, end);
            } else {
                rows = gameScoreMapper.selectRankByRange(gameCode, start, end, direction, useSecondary);
            }
        } else {
            rows = queryAllRank(gameCode);
        }

        List<RankingItem> list = new ArrayList<>(rows.size());
        Map<Long, Integer> userMap = new HashMap<>();
        int rank = 1;
        for (Map<String, Object> row : rows) {
            Long rowUserId = ((Number) row.get("userId")).longValue();
            String nickname = row.get("nickname") != null ? row.get("nickname").toString() : "匿名用户";
            int score = ((Number) row.get("score")).intValue();
            Object sec = row.get("secondaryScore");
            Integer secondary = sec != null ? ((Number) sec).intValue() : null;
            list.add(new RankingItem(rank, rowUserId, nickname, score, secondary));
            userMap.put(rowUserId, rank);
            rank++;
        }
        return new RankingSnapshot(list, userMap, "TODAY".equals(period) ? today : null);
    }

    private List<Map<String, Object>> queryAllRank(String gameCode) {
        switch (gameCode) {
            case "2048": return game2048ScoreMapper.selectAllRank();
            case "color-focus": return colorFocusScoreMapper.selectAllRank();
            case "direction-trap": return directionTrapScoreMapper.selectAllRank();
            case "color-hunter": return colorHunterScoreMapper.selectAllRank();
            case "fish-breakout": return fishBreakoutScoreMapper.selectAllRank();
            case "extreme-fishing": return extremeFishingScoreMapper.selectAllRank();
            case "detail": return detailScoreMapper.selectAllRank();
            case "dont-fill": return dontFillScoreMapper.selectAllRank();
            case "stack-tower": return stackTowerScoreMapper.selectAllRank();
            default: throw new IllegalArgumentException("未知游戏: " + gameCode);
        }
    }
}
