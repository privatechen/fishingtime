package com.fishingtime.banner.dto;

import lombok.Data;

/**
 * 地区信息 DTO — 返回前端
 */
@Data
public class RegionDTO {

    private String name;
    private String adcode;
    private String citycode;
    private Integer level;
    private String parentAdcode;
}
