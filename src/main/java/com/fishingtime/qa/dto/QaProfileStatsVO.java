package com.fishingtime.qa.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 「我的瞅瞅」统计：回答数 + 大众派
 */
@Data
@AllArgsConstructor
public class QaProfileStatsVO {

    /** 累计有效回答数 */
    private Integer answerCount;
    /** 多数派题数（当前票数最高选项，并列任选其一即算） */
    private Integer majorityCount;
    /** 大众派比例（0~100，整数）；未答题为 0 */
    private Integer majorityRate;
    /** 称号（按比例区间；未答题为 null，前端显示「还没瞅过」） */
    private String majorityTitle;
}
