package com.fishingtime.game.dto;

import lombok.Data;

/**
 * 极限捞鱼成绩提交 DTO
 */
@Data
public class ExtremeFishingScoreSubmitDTO {

    /** 本局总得分 */
    private Integer score;

    /** 本局捕获鱼数 */
    private Integer caughtFish;

    /** 本局 PERFECT NET 次数 */
    private Integer perfectCount;

    /** 本局最高 Combo */
    private Integer maxCombo;

    /** 本局河豚失误数 */
    private Integer pufferMistakes;
}
