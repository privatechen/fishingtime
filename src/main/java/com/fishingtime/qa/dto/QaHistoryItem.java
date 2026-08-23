package com.fishingtime.qa.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 「瞅瞅」我的回答历史条目
 */
@Data
public class QaHistoryItem {

    private Long questionId;
    private String content;
    private Long optionId;
    private String optionContent;
    private LocalDateTime answeredAt;
}
