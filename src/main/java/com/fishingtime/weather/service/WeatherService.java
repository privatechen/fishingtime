package com.fishingtime.weather.service;

import com.fishingtime.weather.dto.WeatherDTO;
import javax.servlet.http.HttpServletRequest;

/**
 * 天气服务接口
 */
public interface WeatherService {

    /**
     * 获取当前用户所在地区的实时天气
     * 任一外部接口异常时返回 null（前端隐藏天气模块）
     *
     * @param request 用户请求，用于提取客户端 IP
     * @return 天气信息，失败返回 null
     */
    WeatherDTO getWeather(HttpServletRequest request);
}
