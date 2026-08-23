package com.fishingtime.qa.domain;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 「瞅瞅」问题分类
 */
@Data
public class QaCategory {

    private Long id;
    /** 分类编码（daily/love/money/work/food/social/habit/brain） */
    private String code;
    private String name;
    /** 图标（emoji） */
    private String icon;
    private Integer sortOrder;
    /** 1=启用 0=停用 */
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
