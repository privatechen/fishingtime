package com.fishingtime.qa.dto;

import lombok.Data;

import java.util.List;

/**
 * 「我的投稿」条目（含状态/驳回原因/选项/是否已答）
 */
@Data
public class QaMySubmitVO {

    private Long questionId;
    private Long categoryId;
    private String categoryName;
    private String content;
    /** 0=待审 1=上线 2=驳回 */
    private Integer status;
    private String rejectReason;
    private Integer answerCount;
    /** 我是否已答 */
    private Boolean answered;
    private Long myOptionId;
    /** 选项（已答含比例） */
    private List<QaOptionVO> options;
}
