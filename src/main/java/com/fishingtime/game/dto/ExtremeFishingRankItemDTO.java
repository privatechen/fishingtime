package com.fishingtime.game.dto;

import lombok.Data;

/**
 * 极限捞鱼排行榜条目 DTO
 */
@Data
public class ExtremeFishingRankItemDTO {

    private Integer rank;
    private String nickname;
    /** 最高总分 */
    private Integer score;
}
