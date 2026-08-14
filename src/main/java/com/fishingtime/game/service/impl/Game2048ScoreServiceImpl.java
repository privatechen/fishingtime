package com.fishingtime.game.service.impl;

import com.fishingtime.game.domain.Game2048Score;
import com.fishingtime.game.dto.RankItemDTO;
import com.fishingtime.game.dto.ScoreSubmitDTO;
import com.fishingtime.game.mapper.Game2048ScoreMapper;
import com.fishingtime.game.service.Game2048ScoreService;
import com.fishingtime.game.service.GameScoreLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 2048 分数服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class Game2048ScoreServiceImpl implements Game2048ScoreService {

    private static final int TOP_LIMIT = 20;

    private final Game2048ScoreMapper scoreMapper;
    private final GameScoreLogService gameScoreLogService;

    @Override
    public List<RankItemDTO> getRank() {
        List<Map<String, Object>> rows = scoreMapper.selectRank(TOP_LIMIT);
        List<RankItemDTO> list = new ArrayList<>();
        int rank = 1;
        for (Map<String, Object> row : rows) {
            RankItemDTO dto = new RankItemDTO();
            dto.setRank(rank++);
            Object nickname = row.get("nickname");
            dto.setNickname(nickname != null ? nickname.toString() : "匿名用户");
            Object bestScore = row.get("bestScore");
            dto.setBestScore(bestScore != null ? ((Number) bestScore).intValue() : 0);
            list.add(dto);
        }
        return list;
    }

    @Override
    public Game2048Score getMyBest(Long userId) {
        return scoreMapper.selectByUserId(userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submitScore(Long userId, ScoreSubmitDTO dto) {
        if (dto.getBestScore() == null) return;

        Game2048Score existing = scoreMapper.selectByUserId(userId);
        if (existing == null) {
            Game2048Score score = new Game2048Score();
            score.setUserId(userId);
            score.setBestScore(dto.getBestScore());
            score.setMaxTile(dto.getMaxTile());
            scoreMapper.insert(score);
            log.info("[2048] 用户 {} 首次提交分数 {}", userId, dto.getBestScore());
        } else {
            if (dto.getBestScore() > existing.getBestScore()) {
                scoreMapper.updateBest(userId, dto.getBestScore(), dto.getMaxTile());
                log.info("[2048] 用户 {} 更新最高分 {}", userId, dto.getBestScore());
            }
        }
        // 每局成绩落 game_score 日志（今日榜事实来源）
        gameScoreLogService.record(userId, "2048", dto.getBestScore(), null);
    }
}
