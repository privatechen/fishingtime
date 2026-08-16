package com.fishingtime.game.mapper;

import com.fishingtime.game.domain.DetailScore;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 《细节》每用户最佳成绩 Mapper（总榜数据来源）
 */
@Mapper
public interface DetailScoreMapper {

    DetailScore selectByUserId(@Param("userId") Long userId);

    void insert(DetailScore score);

    /**
     * 若本局刷新最佳则更新：答对数只增不减；同答对数时用时取更小。
     * 返回受影响行数（0=未刷新）。
     */
    int updateBest(@Param("userId") Long userId,
                   @Param("correctCount") int correctCount,
                   @Param("answerTimeMs") int answerTimeMs);

    /** 总榜全量（已按 答对数 DESC → 用时 ASC → 达成时间 ASC 排好序） */
    List<Map<String, Object>> selectAllRank();
}
