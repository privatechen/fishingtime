package com.fishingtime.game.mapper;

import com.fishingtime.game.domain.GameScore;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface GameScoreMapper {
    void insert(GameScore score);

    List<Map<String, Object>> selectRankByRange(@Param("gameCode") String gameCode,
                                                @Param("start") String start,
                                                @Param("end") String end,
                                                @Param("direction") String direction,
                                                @Param("useSecondary") boolean useSecondary);

    long countDistinctUsers(@Param("gameCode") String gameCode,
                            @Param("start") String start,
                            @Param("end") String end);

    /** 主分数 DESC、次级分数 ASC 的最佳一局排名（detail / dont-fill）。 */
    List<Map<String, Object>> selectBestGameRankByRange(@Param("gameCode") String gameCode,
                                                        @Param("start") String start,
                                                        @Param("end") String end);

    /** 主分数 DESC、次级分数 DESC 的最佳一局排名（stack-tower）。 */
    List<Map<String, Object>> selectBestGameRankDescSecondaryByRange(@Param("gameCode") String gameCode,
                                                                     @Param("start") String start,
                                                                     @Param("end") String end);

    Integer selectUserBestInRange(@Param("gameCode") String gameCode,
                                  @Param("userId") Long userId,
                                  @Param("start") String start,
                                  @Param("end") String end,
                                  @Param("direction") String direction);
}
