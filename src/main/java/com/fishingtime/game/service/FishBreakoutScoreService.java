package com.fishingtime.game.service;

import com.fishingtime.game.domain.FishBreakoutScore;
import com.fishingtime.game.dto.FishBreakoutRankItemDTO;
import com.fishingtime.game.dto.FishBreakoutScoreSubmitDTO;

import java.util.List;

/**
 * 鱼群突围分数服务接口
 */
public interface FishBreakoutScoreService {

    /** 获取排行榜 Top20 */
    List<FishBreakoutRankItemDTO> getRank();

    /** 获取用户最佳成绩 */
    FishBreakoutScore getMyBest(Long userId);

    /** 提交本局成绩（仅在更优时更新） */
    void submitScore(Long userId, FishBreakoutScoreSubmitDTO dto);
}
