package com.fishingtime.qa.dto;

import lombok.Data;

/**
 * 「瞅瞅」管理后台：分类保存请求
 */
@Data
public class QaCategorySaveRequest {

    private Long id;
    private String code;
    private String name;
    private String icon;
    private Integer sortOrder;
    private Integer status;
}
