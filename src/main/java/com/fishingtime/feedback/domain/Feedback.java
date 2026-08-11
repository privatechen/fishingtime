package com.fishingtime.feedback.domain;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户反馈实体
 */
@Data
public class Feedback {

    private Long id;
    /** 关联 user.id，游客为 null */
    private Long userId;
    /** 反馈内容 */
    private String content;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
