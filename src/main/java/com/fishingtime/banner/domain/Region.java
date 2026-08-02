package com.fishingtime.banner.domain;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 地区编码实体
 */
@Data
public class Region {

    private Long id;
    private String name;
    private String adcode;
    private String citycode;
    private Integer level;
    private String parentAdcode;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
