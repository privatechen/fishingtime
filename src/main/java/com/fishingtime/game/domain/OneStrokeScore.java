package com.fishingtime.game.domain;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class OneStrokeScore {
    private Long id;
    private Long userId;
    private Integer maxLevel;
    private LocalDateTime achievedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
