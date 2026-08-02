package com.fishingtime.game.dto;

import lombok.Data;

/**
 * 分数提交 DTO
 */
@Data
public class ScoreSubmitDTO {

    /** 最高分 */
    private Integer bestScore;

    /** 达到的最大方块 */
    private Integer maxTile;
}
