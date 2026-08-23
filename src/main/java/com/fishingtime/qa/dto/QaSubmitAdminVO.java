package com.fishingtime.qa.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 「瞅瞅」管理后台：投稿条目（含分类名与解析后的选项）
 */
@Data
public class QaSubmitAdminVO {

    private Long id;
    private Long userId;
    private Long categoryId;
    private String categoryName;
    private String content;
    private String description;
    private List<QaSubmitRequest.QaSubmitOption> options;
    private Integer status;
    private String rejectReason;
    private LocalDateTime createdAt;
}
