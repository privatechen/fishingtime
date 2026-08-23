package com.fishingtime.qa.dto;

import lombok.Data;

import java.util.List;

/**
 * 「瞅瞅」问题详情（答题前不含比例，答题后含）
 */
@Data
public class QaQuestionVO {

    private Long id;
    private Long categoryId;
    private String categoryName;
    private String content;
    /** 已有回答人数 */
    private Integer answerCount;
    /** 当前用户是否已答 */
    private boolean answered;
    /** 当前用户所选选项 id（未答为 null） */
    private Long myOptionId;
    private List<QaOptionVO> options;
}
