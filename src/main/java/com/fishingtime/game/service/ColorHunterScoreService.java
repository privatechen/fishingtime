package com.fishingtime.game.service;

import com.fishingtime.game.domain.ColorHunterScore;
import com.fishingtime.game.dto.ColorHunterRankItemDTO;
import com.fishingtime.game.dto.ColorHunterScoreSubmitDTO;

import java.util.List;

/**
 * 颜色猎手成绩服务接口
 */
public interface ColorHunterScoreService {

    /** 获取排行榜 Top20（按 best_final_time 升序） */
    List<ColorHunterRankItemDTO> getRank();

    /** 获取用户最佳成绩 */
    ColorHunterScore getMyBest(Long userId);

    /** 提交本局成绩（各字段取更小值更新） */
    void submitScore(Long userId, ColorHunterScoreSubmitDTO dto);
}
