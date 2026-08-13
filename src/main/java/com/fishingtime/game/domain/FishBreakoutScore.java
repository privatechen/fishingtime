package com.fishingtime.game.domain;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 鱼群突围最佳成绩实体（每用户一行）
 */
@Data
public class FishBreakoutScore {

    private Long id;
    private Long userId;
    /** 最高清空池数（排行第一依据） */
    private Integer bestClearedPools;
    /** 最佳记录放生鱼总数（排行第二依据） */
    private Integer bestReleasedFish;
    /** 最佳记录失误数（展示用，不参与排行） */
    private Integer bestMistakes;
    /** 最佳记录总用时（毫秒，展示用） */
    private Integer bestDuration;
    private LocalDateTime achievedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
