package com.fishingtime.game.domain;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * “过不了4关”最佳成绩（每用户一行）。
 * 排名规则：过关数降序；同过关数时最高连对降序；再相同按达成时间升序。
 */
@Data
public class MemoryScore {
    private Long id;
    private Long userId;
    private Integer bestPassedCount;
    private Integer bestStreak;
    private Integer maxStage;
    private LocalDateTime achievedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
