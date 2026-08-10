package com.fishingtime.game.dto;

import lombok.Data;

/**
 * 颜色猎手排行榜条目 DTO
 */
@Data
public class ColorHunterRankItemDTO {

    private Integer rank;
    private String nickname;
    /** 最佳最终成绩（毫秒，越小越靠前） */
    private Integer bestFinalTime;
}
