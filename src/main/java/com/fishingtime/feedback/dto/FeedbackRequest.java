package com.fishingtime.feedback.dto;

import lombok.Data;

/**
 * 反馈提交请求
 */
@Data
public class FeedbackRequest {

    /** 反馈内容 */
    private String content;
}
