package com.fishingtime.pricewatch.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fishingtime.common.dto.ErrorCode;
import com.fishingtime.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class TaobaoShortLinkPriceClient {

    private static final Pattern URL_PATTERN = Pattern.compile("https?://[^\\s\\u3000\\\"'<>]+", Pattern.CASE_INSENSITIVE);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String priceToolBaseUrl;

    public TaobaoShortLinkPriceClient(ObjectMapper objectMapper,
                                      @Value("${price-tool.base-url:http://127.0.0.1:8888}") String priceToolBaseUrl) {
        this.objectMapper = objectMapper;
        this.priceToolBaseUrl = trimTrailingSlash(priceToolBaseUrl);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public ResolveResult resolveAndCollect(String input) {
        String shortUrl = extractShortUrl(input);
        try {
            String requestBody = objectMapper.writeValueAsString(java.util.Map.of("input", shortUrl));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(priceToolBaseUrl + "/api/taobao/short-link/price"))
                    .timeout(Duration.ofSeconds(45))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "淘宝短链解析失败，请确认短链有效且本地采价服务已启动");
            }
            JsonNode root = objectMapper.readTree(response.body());
            String itemId = text(root, "itemId");
            if (itemId == null || !itemId.matches("\\d+")) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "淘宝短链已访问，但没有识别到商品 ID");
            }
            return new ResolveResult(itemId, shortUrl);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "淘宝短链解析失败，请确认本地 PriceTool 已启动后重试");
        }
    }

    private String extractShortUrl(String input) {
        if (input == null || input.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "商品链接不能为空");
        }
        Matcher matcher = URL_PATTERN.matcher(input.trim());
        if (!matcher.find()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "淘宝短链格式不正确");
        }
        String value = matcher.group().replaceAll("[，。！？；：、）》】」』]+$", "");
        try {
            URI uri = URI.create(value);
            String host = uri.getHost();
            if (host == null || !"m.tb.cn".equalsIgnoreCase(host)) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "当前淘宝短链仅支持 m.tb.cn");
            }
            return value;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "淘宝短链格式不正确");
        }
    }

    private String text(JsonNode root, String field) {
        JsonNode node = root == null ? null : root.get(field);
        if (node == null || node.isNull()) return null;
        String value = node.asText();
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) return "http://127.0.0.1:8888";
        String result = value.trim();
        while (result.endsWith("/")) result = result.substring(0, result.length() - 1);
        return result;
    }

    public static final class ResolveResult {
        private final String itemId;
        private final String shortUrl;

        public ResolveResult(String itemId, String shortUrl) {
            this.itemId = itemId;
            this.shortUrl = shortUrl;
        }

        public String getItemId() { return itemId; }
        public String getShortUrl() { return shortUrl; }
    }
}
