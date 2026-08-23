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
    /** 我选的答案（选项下标，可选；选中则投稿时记录我的回答） */
    private Integer answerIndex;

    @Data
    public static class QaSubmitOption {
        private String content;
        private String icon;
    }
}
