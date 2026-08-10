package com.fishingtime.game.domain;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 颜色猎手最佳成绩实体（时间制：越小越好）
 */
@Data
public class ColorHunterScore {

    private Long id;
    private Long userId;
    /** 最佳最终成绩（毫秒，排行榜依据） */
    private Integer bestFinalTime;
    /** 最佳实际用时（毫秒） */
    private Integer bestActualTime;
    /** 最少错误次数 */
    private Integer lowestErrorCount;
    /** 最快一轮用时（毫秒） */
    private Integer fastestRound;
    private LocalDateTime achievedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
