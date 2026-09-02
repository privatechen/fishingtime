package com.fishingtime.game.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 我的游戏成绩 — 聚合各游戏的核心指标（一次返回）
 */
@Data
public class GameRecordDTO {
    private String gameType;
    private Integer bestScore;
    private Integer maxTile;
    private BigDecimal bestAccuracy;
    private Integer maxStreak;
    private Integer bestFinalTime;
    private Integer bestActualTime;
    private Integer lowestErrorCount;
    private Integer bestClearedPools;
    private Integer bestReleasedFish;
    private Integer bestMistakes;
    private Integer bestDuration;
    private Integer bestPerfectCount;
    private Integer bestMaxCombo;
    private Integer bestPufferMistakes;
    private Integer bestLevel;
    private Long bestTime;
    private Integer bestClearedLines;

    /** 堆塔：最高层数 */
    private Integer bestFloor;

    /** 堆塔：最高层成绩对应的 Perfect 次数 */
    private Integer stackPerfectCount;

    /** 一笔画：最高通关关卡 */
    private Integer oneStrokeMaxLevel;
}
