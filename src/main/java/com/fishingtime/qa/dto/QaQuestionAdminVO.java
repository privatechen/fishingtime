package com.fishingtime.qa.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 「瞅瞅」管理后台：题目 + 选项
 */
@Data
public class QaQuestionAdminVO {

    private Long id;
    private Long categoryId;
    private String categoryName;
    private String content;
    private String description;
    private Integer status;
    private Integer answerCount;
    private BigDecimal recommendScore;
    private Integer sortOrder;
    private List<QaOptionAdminVO> options;
}
