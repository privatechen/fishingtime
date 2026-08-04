package com.fishingtime.game.domain;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 2048 游戏最高分实体
 */
@Data
public class Game2048Score {

    private Long id;
    private Long userId;
    private Integer bestScore;
    private Integer maxTile;
    private LocalDateTime achievedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
