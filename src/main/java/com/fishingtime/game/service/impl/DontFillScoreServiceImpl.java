package com.fishingtime.game.service.impl;

import com.fishingtime.game.domain.DontFillScore;
import com.fishingtime.game.dto.DontFillScoreSubmitDTO;
import com.fishingtime.game.mapper.DontFillScoreMapper;
import com.fishingtime.game.service.DontFillScoreService;
import com.fishingtime.game.service.GameScoreLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DontFillScoreServiceImpl implements DontFillScoreService {

    private final DontFillScoreMapper scoreMapper;
    private final GameScoreLogService gameScoreLogService;

    @Override
    public DontFillScore getMyBest(Long userId) {
        return scoreMapper.selectByUserId(userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submitScore(Long userId, DontFillScoreSubmitDTO dto) {
        if (dto == null || dto.getLevel() == null || dto.getBestTime() == null) return;
        if (dto.getLevel() < 1 || dto.getBestTime() < 0) return;

        DontFillScore existing = scoreMapper.selectByUserId(userId);
        if (existing == null) {
            DontFillScore score = new DontFillScore();
            score.setUserId(userId);
            score.setLevel(dto.getLevel());
            score.setBestTime(dto.getBestTime());
            score.setClearedLines(dto.getClearedLines() == null ? 0 : dto.getClearedLines());
            scoreMapper.insert(score);
            log.info("[别堆满方块] 用户 {} 首次提交成绩 level={} time={}ms", userId, dto.getLevel(), dto.getBestTime());
        } else {
            scoreMapper.updateBest(userId, dto);
            log.info("[别堆满方块] 用户 {} 提交成绩 level={} time={}ms", userId, dto.getLevel(), dto.getBestTime());
        }

        // 统一排行榜：score=等级（越高越好），secondaryScore=用时毫秒（越低越好）
        int timeForRanking = dto.getBestTime() > Integer.MAX_VALUE ? Integer.MAX_VALUE : dto.getBestTime().intValue();
        gameScoreLogService.record(userId, "dont-fill", dto.getLevel(), timeForRanking);
    }
}
