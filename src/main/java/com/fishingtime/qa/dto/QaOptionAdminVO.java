package com.fishingtime.qa.dto;

import lombok.Data;

/**
 * 「瞅瞅」管理后台：选项
 */
@Data
public class QaOptionAdminVO {

    private Long id;
    private String content;
    private String icon;
    private Integer sortOrder;
    private Integer voteCount;
}
