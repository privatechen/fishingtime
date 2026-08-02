package com.fishingtime.game.service;

import com.fishingtime.game.domain.Game2048Score;
import com.fishingtime.game.dto.RankItemDTO;
import com.fishingtime.game.dto.ScoreSubmitDTO;

import java.util.List;

/**
 * 2048 分数服务接口
 */
public interface Game2048ScoreService {

    /** 获取排行榜 Top20 */
    List<RankItemDTO> getRank();

    /** 获取用户最高分 */
    Game2048Score getMyBest(Long userId);

    /** 提交最高分（仅当高于当前记录时更新） */
    void submitScore(Long userId, String nickname, ScoreSubmitDTO dto);
}
