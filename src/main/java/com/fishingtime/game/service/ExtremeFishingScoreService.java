package com.fishingtime.game.service;

import com.fishingtime.game.domain.ExtremeFishingScore;
import com.fishingtime.game.dto.ExtremeFishingRankItemDTO;
import com.fishingtime.game.dto.ExtremeFishingScoreSubmitDTO;

import java.util.List;

/**
 * 极限捞鱼分数服务接口
 */
public interface ExtremeFishingScoreService {

    /** 获取排行榜 Top20 */
    List<ExtremeFishingRankItemDTO> getRank();

    /** 获取用户最佳成绩 */
    ExtremeFishingScore getMyBest(Long userId);

    /** 提交本局成绩（仅在更优时更新） */
    void submitScore(Long userId, ExtremeFishingScoreSubmitDTO dto);
}
