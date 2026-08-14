package com.fishingtime.game.service;

import com.fishingtime.game.domain.GameScore;
import com.fishingtime.game.leaderboard.GameRankingConfig;
import com.fishingtime.game.leaderboard.LocalRankingCache;
import com.fishingtime.game.mapper.GameScoreMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 小游戏每局成绩日志服务
 *
 * 各游戏成绩提交成功后调用 record() 落一行 game_score（今日榜/总榜统一事实来源）。
 * 落库后判断该局是否刷新了用户的今日/历史最佳：若变化则失效对应 TODAY/ALL 本地缓存
 * （下次排行榜 GET 从 MySQL 重建）；未变化则缓存不动（PRD §20.4 / A32）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GameScoreLogService {

    private static final ZoneId CN = ZoneId.of("Asia/Shanghai");
    private static final String MIN_DATE = "1970-01-01 00:00:00";
    private static final String MAX_DATE = "9999-12-31 23:59:59";

    private final GameScoreMapper gameScoreMapper;
    private final LocalRankingCache cache;
    private final GameRankingConfig config;

    public void record(Long userId, String gameCode, Integer score, Integer secondaryScore) {
        if (userId == null || score == null) return;

        String direction = config.direction(gameCode);
        String today = LocalDate.now(CN).toString();
        String todayStart = today + " 00:00:00";
        String todayEnd = today + " 23:59:59";

        Integer prevToday = gameScoreMapper.selectUserBestInRange(gameCode, userId, todayStart, todayEnd, direction);
        Integer prevAll = gameScoreMapper.selectUserBestInRange(gameCode, userId, MIN_DATE, MAX_DATE, direction);

        GameScore gs = new GameScore();
        gs.setUserId(userId);
        gs.setGameCode(gameCode);
        gs.setScore(score);
        gs.setSecondaryScore(secondaryScore);
        gs.setValid(1);
        gs.setPlayedAt(LocalDateTime.now(CN)); // 统一存北京时间，供当天判定
        gameScoreMapper.insert(gs);

        boolean betterToday = prevToday == null || (direction.equals("asc") ? score < prevToday : score > prevToday);
        boolean betterAll = prevAll == null || (direction.equals("asc") ? score < prevAll : score > prevAll);
        if (betterToday) cache.invalidate(gameCode, "TODAY");
        if (betterAll) cache.invalidate(gameCode, "ALL");
    }
}
