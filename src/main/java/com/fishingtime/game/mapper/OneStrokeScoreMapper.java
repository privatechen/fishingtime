package com.fishingtime.game.mapper;

import com.fishingtime.game.domain.OneStrokeScore;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface OneStrokeScoreMapper {
    OneStrokeScore selectByUserId(@Param("userId") Long userId);
    void insert(OneStrokeScore score);
    void updateBest(@Param("userId") Long userId, @Param("level") Integer level);
}
