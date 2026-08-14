package com.fishingtime.game.mapper;

import com.fishingtime.game.domain.ColorHunterScore;
import com.fishingtime.game.dto.ColorHunterScoreSubmitDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 颜色猎手成绩 Mapper
 */
@Mapper
public interface ColorHunterScoreMapper {

    ColorHunterScore selectByUserId(@Param("userId") Long userId);

    void insert(ColorHunterScore score);

    /** 各字段取更小值更新（时间/错误越小越好） */
    void updateBest(@Param("userId") Long userId, @Param("dto") ColorHunterScoreSubmitDTO dto);

    /** 排行榜 Top20（按 best_final_time 升序，JOIN user 反查昵称） */
    List<Map<String, Object>> selectRank(@Param("limit") int limit);
    List<Map<String, Object>> selectAllRank();
}
