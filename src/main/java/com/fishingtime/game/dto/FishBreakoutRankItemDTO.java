package com.fishingtime.game.dto;

import lombok.Data;

/**
 * 鱼群突围排行榜条目 DTO
 */
@Data
public class FishBreakoutRankItemDTO {

    private Integer rank;
    private String nickname;
    /** 清空池数 */
    private Integer clearedPools;
    /** 放生鱼总数 */
    private Integer releasedFish;
    /** 失误数 */
    private Integer mistakes;
}
