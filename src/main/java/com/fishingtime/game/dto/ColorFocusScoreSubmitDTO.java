package com.fishingtime.game.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 选颜色分数提交 DTO
 */
@Data
public class ColorFocusScoreSubmitDTO {

    /** 本局综合得分 */
    private Integer bestScore;

    /** 本局正确率 0.00~1.00（可空） */
    private BigDecimal bestAccuracy;

    /** 本局平均反应时间（秒，可空） */
    private BigDecimal bestAvgReaction;

    /** 本局规则切换正确率 0.00~1.00（可空） */
    private BigDecimal bestSwitchAccuracy;

    /** 本局最高连对（可空） */
    private Integer maxStreak;
}
