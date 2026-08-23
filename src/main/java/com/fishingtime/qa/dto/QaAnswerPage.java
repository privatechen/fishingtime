package com.fishingtime.qa.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * 「我的回答」分页响应
 */
@Data
@AllArgsConstructor
public class QaAnswerPage {

    private List<QaHistoryItem> items;
    private long total;
    private int page;
    private int pageSize;
}
