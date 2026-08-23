package com.fishingtime.qa.domain;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 「瞅瞅」用户回答（UNIQUE(user_id, question_id) 保证一题一答，幂等）
 */
@Data
public class QaUserAnswer {

    private Long id;
    private Long userId;
    private Long questionId;
    private Long optionId;
    private LocalDateTime createdAt;
}
