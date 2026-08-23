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
    /** 0=草稿 1=上线 2=下线 */
    private Integer status;
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
