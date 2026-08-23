package com.fishingtime.qa.dto;

import lombok.Data;

import java.util.List;

/**
 * 「瞅瞅」用户出题投稿请求
 */
@Data
public class QaSubmitRequest {

    private Long categoryId;
    private String content;
    private String description;
    private List<QaSubmitOption> options;

    @Data
    public static class QaSubmitOption {
        private String content;
        private String icon;
    }
}
