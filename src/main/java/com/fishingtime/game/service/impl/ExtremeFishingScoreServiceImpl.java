package com.fishingtime.game.service.impl;

import com.fishingtime.game.domain.ExtremeFishingScore;
import com.fishingtime.game.dto.ExtremeFishingRankItemDTO;
import com.fishingtime.game.dto.ExtremeFishingScoreSubmitDTO;
import com.fishingtime.game.mapper.ExtremeFishingScoreMapper;
import com.fishingtime.game.service.ExtremeFishingScoreService;
import com.fishingtime.game.service.GameScoreLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 极限捞鱼分数服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExtremeFishingScoreServiceImpl implements ExtremeFishingScoreService {

    private static final int TOP_LIMIT = 20;

    private final ExtremeFishingScoreMapper scoreMapper;
    private final GameScoreLogService gameScoreLogService;

    @Override
    public List<ExtremeFishingRankItemDTO> getRank() {
        List<Map<String, Object>> rows = scoreMapper.selectRank(TOP_LIMIT);
        List<ExtremeFishingRankItemDTO> list = new ArrayList<>();
        int rank = 1;
        for (Map<String, Object> row : rows) {
            ExtremeFishingRankItemDTO dto = new ExtremeFishingRankItemDTO();
            dto.setRank(rank++);
            Object nickname = row.get("nickname");
            dto.setNickname(nickname != null ? nickname.toString() : "匿名用户");
            Object score = row.get("score");
            dto.setScore(score != null ? ((Number) score).intValue() : 0);
            list.add(dto);
        }
        return list;
    }

    @Override
    public ExtremeFishingScore getMyBest(Long userId) {
        return scoreMapper.selectByUserId(userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submitScore(Long userId, ExtremeFishingScoreSubmitDTO dto) {
        if (dto == null || dto.getScore() == null) return;

        ExtremeFishingScore existing = scoreMapper.selectByUserId(userId);
        if (existing == null) {
            ExtremeFishingScore score = new ExtremeFishingScore();
            score.setUserId(userId);
            score.setBestScore(dto.getScore());
            score.setBestCaughtFish(dto.getCaughtFish());
            score.setBestPerfectCount(dto.getPerfectCount());
            score.setBestMaxCombo(dto.getMaxCombo());
            score.setBestPufferMistakes(dto.getPufferMistakes());
            scoreMapper.insert(score);
            log.info("[极限捞鱼] 用户 {} 首次提交成绩 {}", userId, dto.getScore());
        } else {
            scoreMapper.updateBest(userId, dto);
            log.info("[极限捞鱼] 用户 {} 更新最佳成绩 {}", userId, dto.getScore());
        }
        // 每局成绩落 game_score 日志（今日榜事实来源）
        gameScoreLogService.record(userId, "extreme-fishing", dto.getScore(), null);
    }
}
