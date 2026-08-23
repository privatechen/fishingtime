package com.fishingtime.qa.dto;

import lombok.Data;

/**
 * 「瞅瞅」管理后台：分类 + 题目数
 */
@Data
public class QaCategoryAdminVO {

    private Long id;
    private String code;
    private String name;
    private String icon;
    private Integer sortOrder;
    private Integer status;
    private Long questionCount;
}
