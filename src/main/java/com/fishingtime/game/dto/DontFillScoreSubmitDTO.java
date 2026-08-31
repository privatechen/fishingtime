package com.fishingtime.game.dto;

import lombok.Data;

/**
 * 别堆满方块成绩提交 DTO
 */
@Data
public class DontFillScoreSubmitDTO {

    /** 本局达到的等级 */
    private Integer level;

    /** 本局用时（毫秒） */
    private Long bestTime;

    /** 本局消除行数 */
    private Integer clearedLines;
}
