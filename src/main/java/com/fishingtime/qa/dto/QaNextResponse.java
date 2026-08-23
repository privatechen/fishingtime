package com.fishingtime.qa.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 「瞅瞅」下一题响应：finished=true 表示该分类已答完
 */
@Data
@AllArgsConstructor
public class QaNextResponse {

    /** true = 该分类没有更多未答题 */
    private boolean finished;
    /** finished=false 时的题目 */
    private QaQuestionVO question;
}
