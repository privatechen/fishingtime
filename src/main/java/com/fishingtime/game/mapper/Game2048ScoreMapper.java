package com.fishingtime.game.mapper;

import com.fishingtime.game.domain.Game2048Score;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 2048 分数 Mapper
 */
@Mapper
public interface Game2048ScoreMapper {

    Game2048Score selectByUserId(@Param("userId") Long userId);

    void insert(Game2048Score score);

    void updateBest(@Param("userId") Long userId,
                    @Param("bestScore") Integer bestScore,
                    @Param("maxTile") Integer maxTile);

    /** 排行榜 Top20：分降序，同分按达成时间升序 */
    List<Game2048Score> selectTopRank(@Param("limit") int limit);

    /** 排行榜（JOIN user 表反查昵称） */
    List<java.util.Map<String, Object>> selectRank(@Param("limit") int limit);
}
