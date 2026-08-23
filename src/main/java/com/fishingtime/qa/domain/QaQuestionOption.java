package com.fishingtime.qa.domain;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 「瞅瞅」问题选项
 */
@Data
public class QaQuestionOption {

    private Long id;
    private Long questionId;
    private String content;
    /** 图标（emoji，可选） */
    private String icon;
    private Integer sortOrder;
    /** 得票数（事务内更新） */
    private Integer voteCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
