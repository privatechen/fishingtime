package com.fishingtime.game.dto;

import lombok.Data;

/**
 * 排行榜条目 DTO
 */
@Data
public class RankItemDTO {

    private Integer rank;
    private String nickname;
    private Integer bestScore;
}
