package com.fishingtime.qa.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 「瞅瞅」管理后台：题目保存请求（新增/编辑共用；选项整图替换）
 */
@Data
public class QaQuestionSaveRequest {

    private Long categoryId;
    private String content;
    private String description;
    private Integer status;
    private BigDecimal recommendScore;
    private Integer sortOrder;
    private List<QaOptionSave> options;

    @Data
    public static class QaOptionSave {
        private String content;
        private String icon;
        private Integer sortOrder;
    }
}
