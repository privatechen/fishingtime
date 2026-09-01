package com.fishingtime.game.dto;

import lombok.Data;

@Data
public class StackTowerScoreSubmitDTO {
    /** 本局层数 */
    private Integer floor;
    /** 本局 Perfect 次数 */
    private Integer perfectCount;
}
