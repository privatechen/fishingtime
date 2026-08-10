package com.fishingtime.game.service.impl;

import com.fishingtime.game.domain.DirectionTrapScore;
import com.fishingtime.game.dto.DirectionTrapScoreSubmitDTO;
import com.fishingtime.game.dto.RankItemDTO;
import com.fishingtime.game.mapper.DirectionTrapScoreMapper;
import com.fishingtime.game.service.DirectionTrapScoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 方向陷阱分数服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DirectionTrapScoreServiceImpl implements DirectionTrapScoreService {

    private static final int TOP_LIMIT = 20;

    private final DirectionTrapScoreMapper scoreMapper;

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
    public DirectionTrapScore getMyBest(Long userId) {
        return scoreMapper.selectByUserId(userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submitScore(Long userId, DirectionTrapScoreSubmitDTO dto) {
        if (dto == null || dto.getBestScore() == null) return;

        DirectionTrapScore existing = scoreMapper.selectByUserId(userId);
        if (existing == null) {
            DirectionTrapScore score = new DirectionTrapScore();
            score.setUserId(userId);
            score.setBestScore(dto.getBestScore());
            score.setBestAccuracy(dto.getBestAccuracy());
            score.setBestAvgReaction(dto.getBestAvgReaction());
            score.setBestSwitchAccuracy(dto.getBestSwitchAccuracy());
            score.setMaxStreak(dto.getMaxStreak());
            scoreMapper.insert(score);
            log.info("[方向陷阱] 用户 {} 首次提交分数 {}", userId, dto.getBestScore());
        } else {
            scoreMapper.updateBest(userId, dto);
            log.info("[方向陷阱] 用户 {} 更新最佳成绩 {}", userId, dto.getBestScore());
        }
    }
}
