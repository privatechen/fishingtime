package com.fishingtime.weather.dto;

import lombok.Data;

/**
 * 天气信息 DTO — 统一返回结构
 */
@Data
public class WeatherDTO {

    /** 省级名称 */
    private String province;

    /** 城市或区县名称 */
    private String city;

    /** 实时天气现象 */
    private String weather;

    /** 实时温度 ℃ */
    private Double temperature;

    /** 相对湿度 % */
    private Double humidity;
}
