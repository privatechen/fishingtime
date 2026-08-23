package com.fishingtime.qa.domain;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 「瞅瞅」问题
 */
@Data
public class QaQuestion {

    private Long id;
    private Long categoryId;
    private String content;
    private String description;
    /** 0=待审(投稿) 1=上线 2=下线/驳回 */
    private Integer status;
    /** 投稿用户 id（空=平台维护） */
    private Long creatorId;
    /** 审核驳回原因 */
    private String rejectReason;
    /** 回答人数 */
    private Integer answerCount;
    private Integer viewCount;
    /** 推荐权重（推荐策略用） */
    private BigDecimal recommendScore;
    private Integer sortOrder;
    private LocalDateTime publishedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
