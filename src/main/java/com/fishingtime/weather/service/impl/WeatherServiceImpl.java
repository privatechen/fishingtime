package com.fishingtime.weather.service.impl;

import com.fishingtime.weather.client.AmapClient;
import com.fishingtime.weather.dto.WeatherDTO;
import com.fishingtime.weather.service.WeatherService;
import com.fishingtime.weather.util.IpUtils;
import javax.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 天气服务实现
 *
 * 流程：取 IP → 定位 adcode → 查天气 → 缓存
 * 缓存：IP→adcode 24h，adcode→天气 10min
 */
@Slf4j
@Service
public class WeatherServiceImpl implements WeatherService {

    private final AmapClient amapClient;

    /** IP → adcode 缓存（24h） */
    private final ConcurrentHashMap<String, CacheEntry<String>> ipAdcodeCache = new ConcurrentHashMap<>();

    /** adcode → 天气 缓存（10min） */
    private final ConcurrentHashMap<String, CacheEntry<WeatherDTO>> weatherCache = new ConcurrentHashMap<>();

    @Value("${weather.default-adcode:110000}")
    private String defaultAdcode;

    /** IP→adcode 缓存时间：24h */
    private static final long IP_CACHE_MS = 24 * 60 * 60 * 1000L;

    /** adcode→天气 缓存时间：10min */
    private static final long WEATHER_CACHE_MS = 10 * 60 * 1000L;

    public WeatherServiceImpl(AmapClient amapClient) {
        this.amapClient = amapClient;
    }

    @Override
    public WeatherDTO getWeather(HttpServletRequest request) {
        // 1. 获取真实 IP
        String ip = IpUtils.getClientIp(request);

        // 2. 查 IP→adcode 缓存
        String adcode = getCached(ipAdcodeCache, "ip:" + (ip == null ? "unknown" : ip));
        if (adcode == null) {
            // 3. 定位
            adcode = amapClient.getAdcodeByIp(ip);
            if (adcode == null) {
                // 定位失败 → 用默认城市
                log.warn("[天气] 定位失败，使用默认城市 adcode={}", defaultAdcode);
                adcode = defaultAdcode;
            }
            putCache(ipAdcodeCache, "ip:" + (ip == null ? "unknown" : ip), adcode, IP_CACHE_MS);
        }

        // 4. 查 adcode→天气 缓存
        WeatherDTO weather = getCached(weatherCache, adcode);
        if (weather != null) {
            return weather;
        }

        // 5. 调天气接口
        weather = amapClient.getWeatherByAdcode(adcode);
        if (weather == null) {
            return null; // 前端隐藏天气模块
        }

        putCache(weatherCache, adcode, weather, WEATHER_CACHE_MS);
        return weather;
    }

    // ─────────── 缓存辅助 ───────────

    private static class CacheEntry<T> {
        final T value;
        final long expireAt;

        CacheEntry(T value, long ttlMs) {
            this.value = value;
            this.expireAt = System.currentTimeMillis() + ttlMs;
        }

        boolean isExpired() {
            return System.currentTimeMillis() > expireAt;
        }
    }

    private <T> T getCached(ConcurrentHashMap<String, CacheEntry<T>> cache, String key) {
        CacheEntry<T> entry = cache.get(key);
        if (entry == null) return null;
        if (entry.isExpired()) {
            cache.remove(key);
            return null;
        }
        return entry.value;
    }

    private <T> void putCache(ConcurrentHashMap<String, CacheEntry<T>> cache, String key, T value, long ttlMs) {
        cache.put(key, new CacheEntry<>(value, ttlMs));
    }
}
