package com.fishingtime.game.mapper;

import com.fishingtime.game.domain.MemoryScore;
import com.fishingtime.game.dto.MemoryScoreSubmitDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface MemoryScoreMapper {
    MemoryScore selectByUserId(@Param("userId") Long userId);
    void insert(MemoryScore score);
    void updateBest(@Param("userId") Long userId,
                    @Param("dto") MemoryScoreSubmitDTO dto,
                    @Param("maxStage") Integer maxStage);
    List<Map<String, Object>> selectAllRank();
}
