package com.fishingtime.game.mapper;

import com.fishingtime.game.domain.NumberPuzzleScore;
import com.fishingtime.game.dto.NumberPuzzleRankItemDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface NumberPuzzleScoreMapper {
    void insert(NumberPuzzleScore score);
    NumberPuzzleScore selectBest(@Param("userId") Long userId, @Param("difficulty") Integer difficulty);
    List<NumberPuzzleRankItemDTO> selectLeaderboard(@Param("difficulty") Integer difficulty,
                                                     @Param("startTime") LocalDateTime startTime,
                                                     @Param("offset") Integer offset,
                                                     @Param("limit") Integer limit);
}
