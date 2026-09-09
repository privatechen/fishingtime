package com.fishingtime.game.dto;

import lombok.Data;

@Data
public class MemoryScoreSubmitDTO {
    /** 本局成功通过的关数。 */
    private Integer passedCount;
    /** 本局最长连续答对次数。 */
    private Integer bestStreak;
}
