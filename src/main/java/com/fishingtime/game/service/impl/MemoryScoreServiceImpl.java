package com.fishingtime.game.service.impl;

import com.fishingtime.game.domain.MemoryScore;
import com.fishingtime.game.dto.MemoryScoreSubmitDTO;
import com.fishingtime.game.mapper.MemoryScoreMapper;
import com.fishingtime.game.service.GameScoreLogService;
import com.fishingtime.game.service.MemoryScoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryScoreServiceImpl implements MemoryScoreService {

    private static final int MAX_STAGE = 9;
    private static final int MAX_REASONABLE_PASSED = 10000;

    private final MemoryScoreMapper scoreMapper;
    private final GameScoreLogService gameScoreLogService;

    @Override
    public MemoryScore getMyBest(Long userId) {
        return scoreMapper.selectByUserId(userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submitScore(Long userId, MemoryScoreSubmitDTO dto) {
        if (dto == null || dto.getPassedCount() == null) return;

        int passedCount = dto.getPassedCount();
        int bestStreak = dto.getBestStreak() == null ? 0 : dto.getBestStreak();
        if (passedCount < 0 || passedCount > MAX_REASONABLE_PASSED) return;
        if (bestStreak < 0 || bestStreak > passedCount) return;

        // 难度严格线性推进：3×3替换/交换/混合 → 4×4 → 5×5。
        // 第 N 次过关后进入第 N+1 级，最高封顶第 9 级（5×5混合）。
        int maxStage = Math.min(MAX_STAGE, passedCount + 1);

        MemoryScore existing = scoreMapper.selectByUserId(userId);
        if (existing == null) {
            MemoryScore score = new MemoryScore();
            score.setUserId(userId);
            score.setBestPassedCount(passedCount);
            score.setBestStreak(bestStreak);
            score.setMaxStage(maxStage);
            scoreMapper.insert(score);
            log.info("[过不了4关] 用户 {} 首次提交 passed={} streak={} stage={}", userId, passedCount, bestStreak, maxStage);
        } else {
            scoreMapper.updateBest(userId, dto, maxStage);
            log.info("[过不了4关] 用户 {} 提交 passed={} streak={} stage={}", userId, passedCount, bestStreak, maxStage);
        }

        // 统一排行榜：过关数越多越好；同过关数时本局最长连对越多越好。
        gameScoreLogService.record(userId, "memory", passedCount, bestStreak);
    }
}
