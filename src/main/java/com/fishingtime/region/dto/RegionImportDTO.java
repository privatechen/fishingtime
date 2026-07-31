package com.fishingtime.region.dto;

import lombok.Data;

/**
 * 地区导入 DTO — 接收前端传入的单条地区数据
 */
@Data
public class RegionImportDTO {

    private String name;
    private String adcode;
    private String citycode;
}
