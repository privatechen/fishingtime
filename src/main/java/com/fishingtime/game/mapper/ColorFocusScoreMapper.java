package com.fishingtime.game.mapper;

import com.fishingtime.game.domain.ColorFocusScore;
import com.fishingtime.game.dto.ColorFocusScoreSubmitDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 选颜色分数 Mapper
 */
@Mapper
public interface ColorFocusScoreMapper {

    ColorFocusScore selectByUserId(@Param("userId") Long userId);

    void insert(ColorFocusScore score);

    /** 各字段取更优值更新（best_score 只增不减，正确率更大/均时更小/连对更大） */
    void updateBest(@Param("userId") Long userId, @Param("dto") ColorFocusScoreSubmitDTO dto);

    /** 排行榜 Top20（JOIN user 表反查昵称） */
    List<Map<String, Object>> selectRank(@Param("limit") int limit);
    List<Map<String, Object>> selectAllRank();
}
