package com.fishingtime.game.service.impl;

import com.fishingtime.game.domain.StackTowerScore;
import com.fishingtime.game.dto.StackTowerScoreSubmitDTO;
import com.fishingtime.game.mapper.StackTowerScoreMapper;
import com.fishingtime.game.service.GameScoreLogService;
import com.fishingtime.game.service.StackTowerScoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class StackTowerScoreServiceImpl implements StackTowerScoreService {

    private final StackTowerScoreMapper scoreMapper;
    private final GameScoreLogService gameScoreLogService;

    @Override
    public StackTowerScore getMyBest(Long userId) {
        return scoreMapper.selectByUserId(userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submitScore(Long userId, StackTowerScoreSubmitDTO dto) {
        if (dto == null || dto.getFloor() == null) return;
        int floor = dto.getFloor();
        int perfectCount = dto.getPerfectCount() == null ? 0 : dto.getPerfectCount();
        if (floor < 0 || perfectCount < 0) return;

        StackTowerScore existing = scoreMapper.selectByUserId(userId);
        if (existing == null) {
            StackTowerScore score = new StackTowerScore();
            score.setUserId(userId);
            score.setMaxFloor(floor);
            score.setPerfectCount(perfectCount);
            scoreMapper.insert(score);
            log.info("[你能堆多高] 用户 {} 首次提交成绩 floor={} perfect={}", userId, floor, perfectCount);
        } else {
            scoreMapper.updateBest(userId, dto);
            log.info("[你能堆多高] 用户 {} 提交成绩 floor={} perfect={}", userId, floor, perfectCount);
        }

        // 统一排行榜：层数越高越好；层数相同时 Perfect 次数越多越好。
        gameScoreLogService.record(userId, "stack-tower", floor, perfectCount);
    }
}
