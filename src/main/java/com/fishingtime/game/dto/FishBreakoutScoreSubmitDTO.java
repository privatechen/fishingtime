package com.fishingtime.game.dto;

import lombok.Data;

/**
 * 鱼群突围成绩提交 DTO
 */
@Data
public class FishBreakoutScoreSubmitDTO {

    /** 本局清空池数 */
    private Integer clearedPools;

    /** 本局放生鱼总数 */
    private Integer releasedFish;

    /** 本局失误数（展示用，不参与排行） */
    private Integer mistakes;

    /** 本局总用时（毫秒） */
    private Integer duration;
}
