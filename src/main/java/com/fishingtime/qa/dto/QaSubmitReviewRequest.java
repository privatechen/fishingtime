package com.fishingtime.qa.dto;

import lombok.Data;

/**
 * 「瞅瞅」管理后台：投稿审核请求（驳回原因）
 */
@Data
public class QaSubmitReviewRequest {

    private String reason;
}
