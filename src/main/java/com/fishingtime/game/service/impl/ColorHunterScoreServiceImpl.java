package com.fishingtime.game.service.impl;

import com.fishingtime.game.domain.ColorHunterScore;
import com.fishingtime.game.dto.ColorHunterRankItemDTO;
import com.fishingtime.game.dto.ColorHunterScoreSubmitDTO;
import com.fishingtime.game.mapper.ColorHunterScoreMapper;
import com.fishingtime.game.service.ColorHunterScoreService;
import com.fishingtime.game.service.GameScoreLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 颜色猎手成绩服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ColorHunterScoreServiceImpl implements ColorHunterScoreService {

    private static final int TOP_LIMIT = 20;

    private final ColorHunterScoreMapper scoreMapper;
    private final GameScoreLogService gameScoreLogService;

    @Override
    public List<ColorHunterRankItemDTO> getRank() {
        List<Map<String, Object>> rows = scoreMapper.selectRank(TOP_LIMIT);
        List<ColorHunterRankItemDTO> list = new ArrayList<>();
        int rank = 1;
        for (Map<String, Object> row : rows) {
            ColorHunterRankItemDTO dto = new ColorHunterRankItemDTO();
            dto.setRank(rank++);
            Object nickname = row.get("nickname");
            dto.setNickname(nickname != null ? nickname.toString() : "匿名用户");
            Object bestFinalTime = row.get("bestFinalTime");
            dto.setBestFinalTime(bestFinalTime != null ? ((Number) bestFinalTime).intValue() : 0);
            list.add(dto);
        }
        return list;
    }

    @Override
    public ColorHunterScore getMyBest(Long userId) {
        return scoreMapper.selectByUserId(userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submitScore(Long userId, ColorHunterScoreSubmitDTO dto) {
        if (dto == null || dto.getBestFinalTime() == null) return;

        ColorHunterScore existing = scoreMapper.selectByUserId(userId);
        if (existing == null) {
            ColorHunterScore score = new ColorHunterScore();
            score.setUserId(userId);
            score.setBestFinalTime(dto.getBestFinalTime());
            score.setBestActualTime(dto.getBestActualTime());
            score.setLowestErrorCount(dto.getLowestErrorCount());
            score.setFastestRound(dto.getFastestRound());
            scoreMapper.insert(score);
            log.info("[颜色猎手] 用户 {} 首次提交成绩 {}ms", userId, dto.getBestFinalTime());
        } else {
            scoreMapper.updateBest(userId, dto);
            log.info("[颜色猎手] 用户 {} 更新最佳成绩 {}ms", userId, dto.getBestFinalTime());
        }
        // 每局成绩落 game_score 日志（耗时型，score 存 ms）
        gameScoreLogService.record(userId, "color-hunter", dto.getBestFinalTime(), null);
    }
}
