package com.fishingtime.qa.domain;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 「瞅瞅」用户出题投稿（待管理员审核后上线）
 */
@Data
public class QaQuestionSubmit {

    private Long id;
    private Long userId;
    private Long categoryId;
    private String content;
    private String description;
    /** 选项列表 JSON：[{"content":"","icon":""}] */
    private String optionsJson;
    /** 0=待审 1=通过 2=驳回 */
    private Integer status;
    private String rejectReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
