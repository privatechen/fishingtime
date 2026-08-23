package com.fishingtime.qa.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 「我的回答」历史条目
 */
@Data
public class QaHistoryItem {

    private Long questionId;
    private String question;
    private Long optionId;
    /** 我的选择文本 */
    private String myAnswer;
    /** 与我同选择的当前比例（0~100）；题目已下线为 null */
    private Integer sameRate;
    /** 我的选择是否为当前多数派（并列算多数） */
    private Boolean majority;
    private LocalDateTime answeredAt;
}
