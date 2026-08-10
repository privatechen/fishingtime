package com.fishingtime.game.service;

import com.fishingtime.game.domain.ColorFocusScore;
import com.fishingtime.game.dto.ColorFocusScoreSubmitDTO;
import com.fishingtime.game.dto.RankItemDTO;

import java.util.List;

/**
 * 选颜色分数服务接口
 */
public interface ColorFocusScoreService {

    /** 获取排行榜 Top20 */
    List<RankItemDTO> getRank();

    /** 获取用户最佳成绩（含正确率/均时/连对） */
    ColorFocusScore getMyBest(Long userId);

    /** 提交本局成绩（各字段取更优值更新） */
    void submitScore(Long userId, ColorFocusScoreSubmitDTO dto);
}
