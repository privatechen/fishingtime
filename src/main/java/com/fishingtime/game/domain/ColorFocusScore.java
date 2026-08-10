package com.fishingtime.game.domain;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 选颜色最高分实体
 */
@Data
public class ColorFocusScore {

    private Long id;
    private Long userId;
    /** 最高综合得分（排行榜依据） */
    private Integer bestScore;
    /** 最佳正确率 0.00~1.00 */
    private BigDecimal bestAccuracy;
    /** 最佳平均反应时间（秒） */
    private BigDecimal bestAvgReaction;
    /** 最佳规则切换正确率 0.00~1.00 */
    private BigDecimal bestSwitchAccuracy;
    /** 最高连对 */
    private Integer maxStreak;
    private LocalDateTime achievedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
