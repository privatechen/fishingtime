package com.fishingtime.game.service.impl;

import com.fishingtime.game.domain.FishBreakoutScore;
import com.fishingtime.game.dto.FishBreakoutRankItemDTO;
import com.fishingtime.game.dto.FishBreakoutScoreSubmitDTO;
import com.fishingtime.game.mapper.FishBreakoutScoreMapper;
import com.fishingtime.game.service.FishBreakoutScoreService;
import com.fishingtime.game.service.GameScoreLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 鱼群突围分数服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FishBreakoutScoreServiceImpl implements FishBreakoutScoreService {

    private static final int TOP_LIMIT = 20;

    private final FishBreakoutScoreMapper scoreMapper;
    private final GameScoreLogService gameScoreLogService;

    @Override
    public List<FishBreakoutRankItemDTO> getRank() {
        List<Map<String, Object>> rows = scoreMapper.selectRank(TOP_LIMIT);
        List<FishBreakoutRankItemDTO> list = new ArrayList<>();
        int rank = 1;
        for (Map<String, Object> row : rows) {
            FishBreakoutRankItemDTO dto = new FishBreakoutRankItemDTO();
            dto.setRank(rank++);
            Object nickname = row.get("nickname");
            dto.setNickname(nickname != null ? nickname.toString() : "匿名用户");
            dto.setClearedPools(((Number) row.get("clearedPools")).intValue());
            dto.setReleasedFish(((Number) row.get("releasedFish")).intValue());
            Object mistakes = row.get("mistakes");
            dto.setMistakes(mistakes != null ? ((Number) mistakes).intValue() : 0);
            list.add(dto);
        }
        return list;
    }

    @Override
    public FishBreakoutScore getMyBest(Long userId) {
        return scoreMapper.selectByUserId(userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submitScore(Long userId, FishBreakoutScoreSubmitDTO dto) {
        if (dto == null || dto.getClearedPools() == null) return;

        FishBreakoutScore existing = scoreMapper.selectByUserId(userId);
        if (existing == null) {
            FishBreakoutScore score = new FishBreakoutScore();
            score.setUserId(userId);
            score.setBestClearedPools(dto.getClearedPools());
            score.setBestReleasedFish(dto.getReleasedFish());
            score.setBestMistakes(dto.getMistakes());
            score.setBestDuration(dto.getDuration());
            scoreMapper.insert(score);
            log.info("[鱼群突围] 用户 {} 首次提交成绩 清空{}池 放生{}条", userId, dto.getClearedPools(), dto.getReleasedFish());
        } else {
            scoreMapper.updateBest(userId, dto);
            log.info("[鱼群突围] 用户 {} 更新最佳成绩 清空{}池 放生{}条", userId, dto.getClearedPools(), dto.getReleasedFish());
        }
        // 每局成绩落 game_score 日志（score=清空池数，secondary=放生数）
        gameScoreLogService.record(userId, "fish-breakout", dto.getClearedPools(), dto.getReleasedFish());
    }
}
