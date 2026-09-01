package com.fishingtime.game.mapper;

import com.fishingtime.game.domain.StackTowerScore;
import com.fishingtime.game.dto.StackTowerScoreSubmitDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface StackTowerScoreMapper {
    StackTowerScore selectByUserId(@Param("userId") Long userId);
    void insert(StackTowerScore score);
    void updateBest(@Param("userId") Long userId, @Param("dto") StackTowerScoreSubmitDTO dto);
    List<Map<String, Object>> selectAllRank();
}
