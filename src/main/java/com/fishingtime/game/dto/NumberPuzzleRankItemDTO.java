package com.fishingtime.game.dto;

import lombok.Data;

@Data
public class NumberPuzzleRankItemDTO {
    private Long userId;
    private String nickname;
    private Integer elapsedMs;
    private Integer steps;
    private Integer hintCount;
}
