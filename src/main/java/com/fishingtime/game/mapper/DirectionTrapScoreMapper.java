package com.fishingtime.game.mapper;

import com.fishingtime.game.domain.DirectionTrapScore;
import com.fishingtime.game.dto.DirectionTrapScoreSubmitDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 方向陷阱分数 Mapper
 */
@Mapper
public interface DirectionTrapScoreMapper {

    DirectionTrapScore selectByUserId(@Param("userId") Long userId);

    void insert(DirectionTrapScore score);

    /** 各字段取更优值更新（best_score 只增不减，正确率/切换率更大、均时更小、连对更大） */
    void updateBest(@Param("userId") Long userId, @Param("dto") DirectionTrapScoreSubmitDTO dto);

    /** 排行榜 Top20（JOIN user 表反查昵称） */
    List<Map<String, Object>> selectRank(@Param("limit") int limit);
}
