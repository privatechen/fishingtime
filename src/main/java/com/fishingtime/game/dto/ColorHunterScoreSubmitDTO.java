package com.fishingtime.game.dto;

import lombok.Data;

/**
 * 颜色猎手成绩提交 DTO（时间制，越小越好）
 */
@Data
public class ColorHunterScoreSubmitDTO {

    /** 本局最终成绩（毫秒） */
    private Integer bestFinalTime;

    /** 本局实际用时（毫秒，可空） */
    private Integer bestActualTime;

    /** 本局错误次数（可空） */
    private Integer lowestErrorCount;

    /** 本局最快一轮（毫秒，可空） */
    private Integer fastestRound;
}
