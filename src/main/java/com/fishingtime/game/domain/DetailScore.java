package com.fishingtime.game.domain;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 《细节》每用户最佳成绩实体（总榜数据来源，一人一行）
 *
 * 排行规则：bestCorrectCount 降序 → bestAnswerTimeMs 升序 → achievedAt 升序
 */
@Data
public class DetailScore {

    private Long id;
    private Long userId;
    /** 最佳答对题数（排行第一依据） */
    private Integer bestCorrectCount;
    /** 最佳成绩的累计答题用时（毫秒，排行第二依据） */
    private Integer bestAnswerTimeMs;
    private LocalDateTime achievedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
