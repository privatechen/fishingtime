package com.fishingtime.game.mapper;

import com.fishingtime.game.domain.DontFillScore;
import com.fishingtime.game.dto.DontFillScoreSubmitDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface DontFillScoreMapper {
    DontFillScore selectByUserId(@Param("userId") Long userId);
    void insert(DontFillScore score);
    void updateBest(@Param("userId") Long userId, @Param("dto") DontFillScoreSubmitDTO dto);
    List<Map<String, Object>> selectAllRank();
}
