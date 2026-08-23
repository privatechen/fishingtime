package com.fishingtime.qa.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 「瞅瞅」分类
 */
@Data
@AllArgsConstructor
public class QaCategoryVO {

    private Long id;
    private String code;
    private String name;
    private String icon;
}
