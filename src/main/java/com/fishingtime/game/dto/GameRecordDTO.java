package com.fishingtime.game.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 我的游戏成绩 — 聚合各游戏的核心指标（一次返回）
 */
@Data
public class GameRecordDTO {

    /** 游戏标识 */
    private String gameType;

    /** 2048 / 专注色彩 / 方向陷阱：核心成绩（得分制，越大越好） */
    private Integer bestScore;

    /** 2048：最大方块 */
    private Integer maxTile;

    /** 专注色彩：最佳正确率 0~1 */
    private BigDecimal bestAccuracy;

    /** 专注色彩 / 方向陷阱：最高连对 */
    private Integer maxStreak;

    /** 颜色猎手：最佳最终成绩（毫秒，越小越好） */
    private Integer bestFinalTime;

    /** 颜色猎手：最佳实际用时（毫秒） */
    private Integer bestActualTime;

    /** 颜色猎手：最少错误次数 */
    private Integer lowestErrorCount;

    /** 鱼群突围：最高清空池数 */
    private Integer bestClearedPools;

    /** 鱼群突围：最佳记录放生鱼数 */
    private Integer bestReleasedFish;

    /** 鱼群突围：最佳记录失误数 */
    private Integer bestMistakes;

    /** 鱼群突围：最佳记录总用时（毫秒） */
    private Integer bestDuration;

    /** 极限捞鱼：最佳 PERFECT NET 次数 */
    private Integer bestPerfectCount;

    /** 极限捞鱼：最佳最高 Combo */
    private Integer bestMaxCombo;

    /** 极限捞鱼：最佳记录河豚失误数 */
    private Integer bestPufferMistakes;

    /** 进阶俄罗斯：最高等级 */
    private Integer bestLevel;

    /** 进阶俄罗斯：达到最高等级的最佳用时（毫秒） */
    private Long bestTime;

    /** 进阶俄罗斯：最佳局消除行数 */
    private Integer bestClearedLines;
}
