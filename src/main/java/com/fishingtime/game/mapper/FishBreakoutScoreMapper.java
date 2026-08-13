package com.fishingtime.game.mapper;

import com.fishingtime.game.domain.FishBreakoutScore;
import com.fishingtime.game.dto.FishBreakoutScoreSubmitDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 鱼群突围分数 Mapper
 */
@Mapper
public interface FishBreakoutScoreMapper {

    FishBreakoutScore selectByUserId(@Param("userId") Long userId);

    void insert(FishBreakoutScore score);

    /** 仅在严格更优时更新（清空池数更高，或相同且放生数更高，与排行规则一致） */
    void updateBest(@Param("userId") Long userId, @Param("dto") FishBreakoutScoreSubmitDTO dto);

    /** 排行榜 Top20（JOIN user 表反查昵称） */
    List<Map<String, Object>> selectRank(@Param("limit") int limit);
}
