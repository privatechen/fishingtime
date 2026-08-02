package com.fishingtime.banner.client;

import com.fishingtime.banner.dto.WeatherDTO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * 高德地图 API 客户端
 * 负责调用 IP 定位接口和实时天气接口
 */
@Slf4j
@Component
public class AmapClient {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${amap.key}")
    private String amapKey;

    @Value("${amap.ip-api:https://restapi.amap.com/v3/ip}")
    private String ipApiUrl;

    @Value("${amap.weather-api:https://restapi.amap.com/v3/weather/weatherInfo}")
    private String weatherApiUrl;

    @Value("${weather.timeout-ms:2000}")
    private int timeoutMs;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(2000))
            .build();

    /**
     * 根据用户 IP 查询 adcode
     *
     * @return 定位成功的 adcode，失败返回 null
     */
    public String getAdcodeByIp(String ip) {
        try {
            String url = ipApiUrl + "?ip=" + ip + "&output=json&key=" + amapKey;
            String body = doGet(url);
            if (body == null) return null;

            JsonNode root = objectMapper.readTree(body);
            String status = root.path("status").asText();
            String adcode = root.path("adcode").asText();

            if ("1".equals(status) && adcode != null && !adcode.isEmpty()) {
                log.info("[天气] IP 定位成功: ip={}, adcode={}", maskIp(ip), adcode);
                return adcode;
            }
            log.warn("[天气] IP 定位失败: status={}, info={}", status, root.path("info").asText());
            return null;
        } catch (Exception e) {
            log.warn("[天气] IP 定位异常: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 根据 adcode 查询实时天气
     *
     * @return 天气信息，失败返回 null
     */
    public WeatherDTO getWeatherByAdcode(String adcode) {
        try {
            String url = weatherApiUrl + "?city=" + adcode + "&extensions=base&key=" + amapKey;
            String body = doGet(url);
            if (body == null) return null;

            JsonNode root = objectMapper.readTree(body);
            String status = root.path("status").asText();
            if (!"1".equals(status)) {
                log.warn("[天气] 天气接口失败: status={}, info={}", status, root.path("info").asText());
                return null;
            }

            JsonNode lives = root.path("lives");
            if (!lives.isArray() || lives.isEmpty()) {
                log.warn("[天气] 天气接口 lives 为空, adcode={}", adcode);
                return null;
            }

            JsonNode first = lives.get(0);
            WeatherDTO dto = new WeatherDTO();
            dto.setProvince(first.path("province").asText());
            dto.setCity(first.path("city").asText());
            dto.setWeather(first.path("weather").asText());
            dto.setTemperature(first.path("temperature_float").asDouble(0));
            dto.setHumidity(first.path("humidity_float").asDouble(0));

            log.info("[天气] 天气查询成功: adcode={}, weather={}, temp={}℃", adcode, dto.getWeather(), dto.getTemperature());
            return dto;
        } catch (Exception e) {
            log.warn("[天气] 天气查询异常: {}", e.getMessage());
            return null;
        }
    }

    private String doGet(String url) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMillis(timeoutMs))
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                log.warn("[天气] 高德接口返回 status={}", resp.statusCode());
                return null;
            }
            return resp.body();
        } catch (Exception e) {
            log.warn("[天气] 高德接口请求异常: {}", e.getMessage());
            return null;
        }
    }

    /** IP 脱敏，日志不记录完整 IP */
    private String maskIp(String ip) {
        if (ip == null || ip.isEmpty()) return "";
        int idx = ip.lastIndexOf('.');
        if (idx > 0) {
            return ip.substring(0, idx + 1) + "*";
        }
        return "***";
    }
}
