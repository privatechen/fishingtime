package com.fishingtime.game.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 我的游戏成绩 — 聚合四款游戏的核心指标（一次返回）
 */
@Data
public class GameRecordDTO {

    /** 游戏标识：2048 / color-focus / direction-trap / color-hunter */
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
}
