package com.fishingtime.game.mapper;

import com.fishingtime.game.domain.ExtremeFishingScore;
import com.fishingtime.game.dto.ExtremeFishingScoreSubmitDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 极限捞鱼分数 Mapper
 */
@Mapper
public interface ExtremeFishingScoreMapper {

    ExtremeFishingScore selectByUserId(@Param("userId") Long userId);

    void insert(ExtremeFishingScore score);

    /** 仅在严格更优时更新（总分更高，或同分且河豚失误更少） */
    void updateBest(@Param("userId") Long userId, @Param("dto") ExtremeFishingScoreSubmitDTO dto);

    /** 排行榜 Top20（JOIN user 表反查昵称） */
    List<Map<String, Object>> selectRank(@Param("limit") int limit);
    List<Map<String, Object>> selectAllRank();
}
