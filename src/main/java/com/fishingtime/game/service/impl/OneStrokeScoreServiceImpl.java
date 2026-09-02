package com.fishingtime.game.service.impl;

import com.fishingtime.game.domain.OneStrokeScore;
import com.fishingtime.game.dto.OneStrokeScoreSubmitDTO;
import com.fishingtime.game.mapper.OneStrokeScoreMapper;
import com.fishingtime.game.service.GameScoreLogService;
import com.fishingtime.game.service.OneStrokeScoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class OneStrokeScoreServiceImpl implements OneStrokeScoreService {

    private final OneStrokeScoreMapper scoreMapper;
    private final GameScoreLogService gameScoreLogService;

    @Override
    public OneStrokeScore getMyBest(Long userId) {
        return scoreMapper.selectByUserId(userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submitScore(Long userId, OneStrokeScoreSubmitDTO dto) {
        if (dto == null || dto.getLevel() == null) return;
        int level = dto.getLevel();
        if (level < 1 || level > 40) return;

        OneStrokeScore existing = scoreMapper.selectByUserId(userId);
        if (existing == null) {
            OneStrokeScore score = new OneStrokeScore();
            score.setUserId(userId);
            score.setMaxLevel(level);
            scoreMapper.insert(score);
            log.info("[一笔画] 用户 {} 首次提交成绩 level={}", userId, level);
        } else {
            scoreMapper.updateBest(userId, level);
            log.info("[一笔画] 用户 {} 提交成绩 level={}", userId, level);
        }

        // 统一成绩日志：关卡越高越好，用于今日战绩和排行榜。
        gameScoreLogService.record(userId, "one-stroke", level, null);
    }
}
