package com.fishingtime.weather.controller;

import com.fishingtime.common.dto.ApiResponse;
import com.fishingtime.weather.dto.WeatherDTO;
import com.fishingtime.weather.service.WeatherService;
import javax.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 天气 API
 *
 * GET /api/weather — 获取当前用户地区实时天气
 */
@Slf4j
@RestController
@RequestMapping("/api/weather")
@RequiredArgsConstructor
public class WeatherController {

    private final WeatherService weatherService;

    @GetMapping
    public ApiResponse<WeatherDTO> getWeather(HttpServletRequest request) {
        WeatherDTO weather = weatherService.getWeather(request);
        if (weather == null) {
            // 接口异常时返回空数据，前端隐藏天气模块
            return ApiResponse.error(5001, "天气信息暂时不可用");
        }
        return ApiResponse.success(weather);
    }
}
