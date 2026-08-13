package com.fishingtime.game.domain;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 极限捞鱼最佳成绩实体（每用户一行）
 */
@Data
public class ExtremeFishingScore {

    private Long id;
    private Long userId;
    /** 最高总分（排行第一依据） */
    private Integer bestScore;
    /** 最佳记录捕获鱼数 */
    private Integer bestCaughtFish;
    /** 最佳记录 PERFECT NET 次数 */
    private Integer bestPerfectCount;
    /** 最佳记录最高 Combo */
    private Integer bestMaxCombo;
    /** 最佳记录河豚失误数（排行第二依据，同分时少者优） */
    private Integer bestPufferMistakes;
    private LocalDateTime achievedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
