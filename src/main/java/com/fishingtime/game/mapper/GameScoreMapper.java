package com.fishingtime.game.mapper;

import com.fishingtime.game.domain.GameScore;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 小游戏每局成绩日志 Mapper
 */
@Mapper
public interface GameScoreMapper {

    /** 记录一局成绩（每次提交 INSERT 一行） */
    void insert(GameScore score);

    /**
     * 按时间范围聚合每用户最佳成绩（今日榜 / 总榜统一入口）。
     * direction: 'asc'（耗时型 MIN + ASC）/ 'desc'（分数型 MAX + DESC）
     * useSecondary: 是否在排序中纳入次级指标（鱼群突围的放生数）
     */
    List<Map<String, Object>> selectRankByRange(@Param("gameCode") String gameCode,
                                                @Param("start") String start,
                                                @Param("end") String end,
                                                @Param("direction") String direction,
                                                @Param("useSecondary") boolean useSecondary);

    /** 范围内参与排名的用户数（分页 total） */
    long countDistinctUsers(@Param("gameCode") String gameCode,
                            @Param("start") String start,
                            @Param("end") String end);

    /**
     * 混合方向最佳一局排名（《细节》今日榜）。
     * 主指标与次级指标方向相反（答对数 DESC → 用时 ASC），无法用 MAX/MIN 聚合表达，
     * 故用 MySQL8 窗口函数每人取「主指标最高、同分次级最小、再同时达成最早」那一局。
     */
    List<Map<String, Object>> selectBestGameRankByRange(@Param("gameCode") String gameCode,
                                                        @Param("start") String start,
                                                        @Param("end") String end);

    /** 用户在某时间范围内的最佳成绩（提交后判断今日/历史最佳是否变化） */
    Integer selectUserBestInRange(@Param("gameCode") String gameCode,
                                  @Param("userId") Long userId,
                                  @Param("start") String start,
                                  @Param("end") String end,
                                  @Param("direction") String direction);
}
