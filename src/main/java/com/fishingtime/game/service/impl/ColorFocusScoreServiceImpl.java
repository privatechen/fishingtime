package com.fishingtime.game.service.impl;

import com.fishingtime.game.domain.ColorFocusScore;
import com.fishingtime.game.dto.ColorFocusScoreSubmitDTO;
import com.fishingtime.game.dto.RankItemDTO;
import com.fishingtime.game.mapper.ColorFocusScoreMapper;
import com.fishingtime.game.service.ColorFocusScoreService;
import com.fishingtime.game.service.GameScoreLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 选颜色分数服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ColorFocusScoreServiceImpl implements ColorFocusScoreService {

    private static final int TOP_LIMIT = 20;

    private final ColorFocusScoreMapper scoreMapper;
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
    public ColorFocusScore getMyBest(Long userId) {
        return scoreMapper.selectByUserId(userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submitScore(Long userId, ColorFocusScoreSubmitDTO dto) {
        if (dto == null || dto.getBestScore() == null) return;

        ColorFocusScore existing = scoreMapper.selectByUserId(userId);
        if (existing == null) {
            ColorFocusScore score = new ColorFocusScore();
            score.setUserId(userId);
            score.setBestScore(dto.getBestScore());
            score.setBestAccuracy(dto.getBestAccuracy());
            score.setBestAvgReaction(dto.getBestAvgReaction());
            score.setBestSwitchAccuracy(dto.getBestSwitchAccuracy());
            score.setMaxStreak(dto.getMaxStreak());
            scoreMapper.insert(score);
            log.info("[选颜色] 用户 {} 首次提交分数 {}", userId, dto.getBestScore());
        } else {
            scoreMapper.updateBest(userId, dto);
            log.info("[选颜色] 用户 {} 更新最佳成绩 {}", userId, dto.getBestScore());
        }
        // 每局成绩落 game_score 日志（今日榜事实来源）
        gameScoreLogService.record(userId, "color-focus", dto.getBestScore(), null);
    }
}
