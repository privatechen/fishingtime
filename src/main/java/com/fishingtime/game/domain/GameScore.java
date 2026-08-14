package com.fishingtime.game.domain;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 小游戏每局成绩日志（今日榜 / 总榜统一事实来源）
 */
@Data
public class GameScore {

    private Long id;
    private Long userId;
    /** 游戏标识：2048 / color-focus / direction-trap / color-hunter / fish-breakout / extreme-fishing */
    private String gameCode;
    /** 本局主排行值（分数型=分数；颜色猎手=耗时ms；鱼群突围=清空池数） */
    private Integer score;
    /** 次级指标（鱼群突围=放生数；其余 NULL） */
    private Integer secondaryScore;
    /** 本局完成时间（北京时间当天判定依据） */
    private LocalDateTime playedAt;
    /** 是否有效成绩（1=有效） */
    private Integer valid;
}
