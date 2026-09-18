package com.fishingtime.pricewatch.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fishingtime.config.WechatProperties;
import com.fishingtime.pricewatch.mapper.PriceNotificationMapper;
import com.fishingtime.user.domain.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class WechatSubscribeMessageService {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final String SEND_API = "https://api.weixin.qq.com/cgi-bin/message/subscribe/send";
    private static final String TOKEN_API = "https://api.weixin.qq.com/cgi-bin/token";

    private final WechatProperties wechatProperties;
    private final ObjectMapper objectMapper;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private final Map<String, CachedToken> tokenCache = new ConcurrentHashMap<>();

    public SendResult send(User user,
                           PriceNotificationMapper.NotificationRow notification,
                           String templateId) {
        if (user == null || user.getOpenid() == null || user.getOpenid().isBlank()
                || user.getWxAppid() == null || user.getWxAppid().isBlank()) {
            return new SendResult(false, -1, "用户缺少微信 OpenID 或 AppID");
        }

        String appId = user.getWxAppid();
        String accessToken = getAccessToken(appId, false);
        SendResult result = doSend(accessToken, user.getOpenid(), notification, templateId);

        // access_token 失效时刷新一次再重试。
        if (!result.success() && (result.errCode() == 40001 || result.errCode() == 42001)) {
            tokenCache.remove(appId);
            accessToken = getAccessToken(appId, true);
            result = doSend(accessToken, user.getOpenid(), notification, templateId);
        }
        return result;
    }

    private SendResult doSend(String accessToken,
                              String openid,
                              PriceNotificationMapper.NotificationRow notification,
                              String templateId) {
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("touser", openid);
            body.put("template_id", templateId);
            body.put("page", "pages/index/index?watchId=" + notification.getWatchId());
            body.put("miniprogram_state", "formal");
            body.put("lang", "zh_CN");

            ObjectNode data = body.putObject("data");
            putValue(data, "thing3", buildWarmTip(notification.getType()));
            putValue(data, "amount6", amount(notification.getDropAmount()));
            putValue(data, "time9", time(notification.getCreatedAt()));
            putValue(data, "thing1", truncateThing(notification.getProductTitle(), 20));
            putValue(data, "amount8", amount(notification.getComparisonPrice()));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(SEND_API + "?access_token=" + encode(accessToken)))
                    .timeout(Duration.ofSeconds(8))
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode root = objectMapper.readTree(response.body());
            int errCode = root.path("errcode").asInt(-1);
            String errMsg = root.path("errmsg").asText("");
            return new SendResult(errCode == 0, errCode, errMsg);
        } catch (Exception e) {
            throw new IllegalStateException("微信订阅消息发送异常: " + e.getMessage(), e);
        }
    }

    private synchronized String getAccessToken(String appId, boolean forceRefresh) {
        CachedToken cached = tokenCache.get(appId);
        LocalDateTime now = LocalDateTime.now();
        if (!forceRefresh && cached != null && cached.expiresAt().isAfter(now.plusMinutes(2))) {
            return cached.token();
        }

        String secret = wechatProperties.secretFor(appId);
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("未配置 PriceGuard 小程序 secret, appId=" + appId);
        }

        try {
            String url = TOKEN_API
                    + "?grant_type=client_credential"
                    + "&appid=" + encode(appId)
                    + "&secret=" + encode(secret);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(8))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode root = objectMapper.readTree(response.body());
            String token = root.path("access_token").asText(null);
            int expiresIn = root.path("expires_in").asInt(7200);
            if (token == null || token.isBlank()) {
                throw new IllegalStateException("获取微信 access_token 失败: errcode="
                        + root.path("errcode").asText() + ", errmsg=" + root.path("errmsg").asText());
            }
            tokenCache.put(appId, new CachedToken(token, now.plusSeconds(Math.max(60, expiresIn - 300))));
            return token;
        } catch (Exception e) {
            throw new IllegalStateException("获取微信 access_token 异常: " + e.getMessage(), e);
        }
    }

    private void putValue(ObjectNode data, String key, String value) {
        data.putObject(key).put("value", value == null ? "" : value);
    }

    private String buildWarmTip(String type) {
        if ("BELOW_PURCHASE".equals(type)) {
            return "采价已低于参考购买价，请前往平台确认";
        }
        return "采价已下降，请前往平台确认";
    }

    private String amount(BigDecimal value) {
        return value == null ? "0.00" : value.stripTrailingZeros().toPlainString();
    }

    private String time(LocalDateTime value) {
        return value == null ? LocalDateTime.now().format(TIME_FORMATTER) : value.format(TIME_FORMATTER);
    }

    private String truncateThing(String value, int maxCodePoints) {
        String text = value == null || value.isBlank() ? "监控商品" : value.trim();
        int count = text.codePointCount(0, text.length());
        if (count <= maxCodePoints) return text;
        int end = text.offsetByCodePoints(0, maxCodePoints);
        return text.substring(0, end);
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private record CachedToken(String token, LocalDateTime expiresAt) {}

    public record SendResult(boolean success, int errCode, String errMsg) {}
}
