package com.fishingtime.game.domain;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 别堆满方块最佳成绩实体（每用户一行）
 * 排名规则：等级降序；等级相同时，用时升序。
 */
@Data
public class DontFillScore {

    private Long id;
    private Long userId;
    /** 历史最高等级 */
    private Integer level;
    /** 达到该等级的最佳用时（毫秒，越短越好） */
    private Long bestTime;
    /** 该最佳局消除行数（展示用） */
    private Integer clearedLines;
    private LocalDateTime achievedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
