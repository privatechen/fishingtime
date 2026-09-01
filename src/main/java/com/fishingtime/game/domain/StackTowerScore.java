package com.fishingtime.game.domain;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 堆塔最佳成绩（每用户一行）。
 * 排名规则：最高层数降序；层数相同时 Perfect 次数降序；再相同按达成时间升序。
 */
@Data
public class StackTowerScore {
    private Long id;
    private Long userId;
    private Integer maxFloor;
    private Integer perfectCount;
    private LocalDateTime achievedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
