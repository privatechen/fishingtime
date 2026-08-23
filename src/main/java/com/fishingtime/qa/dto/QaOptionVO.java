package com.fishingtime.qa.dto;

import lombok.Data;

/**
 * 「瞅瞅」选项（回答前 percent 为 null，前端不展示比例）
 */
@Data
public class QaOptionVO {

    private Long id;
    private String content;
    private String icon;
    private Integer sortOrder;
    /** 得票数（仅回答后返回） */
    private Integer voteCount;
    /** 百分比（仅回答后返回；answerCount=0 时为 null） */
    private Double percent;
}
