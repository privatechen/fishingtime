package com.fishingtime.game.dto;

import lombok.Data;

@Data
public class NumberPuzzleScoreSubmitDTO {
    private Integer difficulty;
    private Integer elapsedMs;
    private Integer steps;
    private Integer hintCount;
}
