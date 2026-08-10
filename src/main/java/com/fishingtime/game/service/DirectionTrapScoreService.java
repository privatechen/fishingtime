package com.fishingtime.game.service;

import com.fishingtime.game.domain.DirectionTrapScore;
import com.fishingtime.game.dto.DirectionTrapScoreSubmitDTO;
import com.fishingtime.game.dto.RankItemDTO;

import java.util.List;

/**
 * 方向陷阱分数服务接口
 */
public interface DirectionTrapScoreService {

    /** 获取排行榜 Top20 */
    List<RankItemDTO> getRank();

    /** 获取用户最佳成绩 */
    DirectionTrapScore getMyBest(Long userId);

    /** 提交本局成绩（各字段取更优值更新） */
    void submitScore(Long userId, DirectionTrapScoreSubmitDTO dto);
}
