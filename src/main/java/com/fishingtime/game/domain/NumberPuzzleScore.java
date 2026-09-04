package com.fishingtime.game.domain;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class NumberPuzzleScore {
    private Long id;
    private Long userId;
    private Integer difficulty;
    private Integer elapsedMs;
    private Integer steps;
    private Integer hintCount;
    private LocalDateTime createdAt;
}
