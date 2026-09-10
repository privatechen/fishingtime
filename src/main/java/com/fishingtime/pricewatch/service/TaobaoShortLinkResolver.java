package com.fishingtime.pricewatch.service;

import com.fishingtime.common.dto.ErrorCode;
import com.fishingtime.common.exception.BusinessException;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class TaobaoShortLinkResolver {

    private static final Pattern URL_PATTERN = Pattern.compile("https?://[^\\s]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern ITEM_ID_PATTERN = Pattern.compile("[?&]id=(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern HTML_ITEM_URL_PATTERN = Pattern.compile(
            "(?:item\\.taobao\\.com|detail\\.tmall\\.com)[^\\\"'<>\\s]*?[?&]id=(\\d+)",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern HTML_ITEM_ID_PATTERN = Pattern.compile(
            "[\\\"']?(?:itemId|item_id)[\\\"']?\\s*[:=]\\s*[\\\"']?(\\d+)",
            Pattern.CASE_INSENSITIVE
    );

    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public ResolveResult resolve(String input) {
        String sourceUrl = extractUrl(input);
        validateTaobaoHost(sourceUrl);

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(sourceUrl))
                    .timeout(Duration.ofSeconds(15))
                    .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/152.0.0.0 Safari/537.36")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            String finalUrl = response.uri().toString();

            String itemId = extractItemId(finalUrl);
            if (itemId == null) {
                itemId = extractItemIdFromHtml(response.body());
            }
            if (itemId == null) {
                throw new BusinessException(ErrorCode.PARAM_INVALID,
                        "淘宝短链已访问，但没有找到商品ID。finalUrl=" + finalUrl);
            }

            String normalizedUrl = "https://item.taobao.com/item.htm?id=" + itemId;
            return new ResolveResult(itemId, sourceUrl, finalUrl, normalizedUrl, response.statusCode());
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "解析淘宝短链失败: " + e.getMessage());
        }
    }

    private String extractUrl(String input) {
        if (input == null || input.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "链接不能为空");
        }
        Matcher matcher = URL_PATTERN.matcher(input);
        if (!matcher.find()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "分享内容中没有找到有效URL");
        }
        String url = matcher.group();
        while (url.endsWith("，") || url.endsWith("。") || url.endsWith(")") || url.endsWith("）")
                || url.endsWith("\"") || url.endsWith("'") || url.endsWith("]") || url.endsWith("】")) {
            url = url.substring(0, url.length() - 1);
        }
        return url;
    }

    private void validateTaobaoHost(String url) {
        try {
            String host = URI.create(url).getHost();
            if (host == null) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "无效的淘宝链接");
            }
            String lower = host.toLowerCase(Locale.ROOT);
            if (!(lower.equals("m.tb.cn") || lower.endsWith(".taobao.com") || lower.equals("taobao.com")
                    || lower.endsWith(".tmall.com") || lower.equals("tmall.com"))) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "当前接口仅用于解析淘宝/天猫链接");
            }
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "无效的淘宝链接");
        }
    }

    private String extractItemId(String url) {
        if (url == null || url.trim().isEmpty()) return null;
        String decoded = URLDecoder.decode(url, StandardCharsets.UTF_8);
        Matcher matcher = ITEM_ID_PATTERN.matcher(decoded);
        return matcher.find() ? matcher.group(1) : null;
    }

    private String extractItemIdFromHtml(String html) {
        if (html == null || html.trim().isEmpty()) return null;
        String normalized = html.replace("\\u002F", "/").replace("\\u002f", "/").replace("\\/", "/");
        Matcher urlMatcher = HTML_ITEM_URL_PATTERN.matcher(normalized);
        if (urlMatcher.find()) return urlMatcher.group(1);
        Matcher itemMatcher = HTML_ITEM_ID_PATTERN.matcher(normalized);
        return itemMatcher.find() ? itemMatcher.group(1) : null;
    }

    public static class ResolveResult {
        private final String itemId;
        private final String sourceUrl;
        private final String finalUrl;
        private final String normalizedUrl;
        private final int statusCode;

        public ResolveResult(String itemId, String sourceUrl, String finalUrl, String normalizedUrl, int statusCode) {
            this.itemId = itemId;
            this.sourceUrl = sourceUrl;
            this.finalUrl = finalUrl;
            this.normalizedUrl = normalizedUrl;
            this.statusCode = statusCode;
        }

        public String getItemId() { return itemId; }
        public String getSourceUrl() { return sourceUrl; }
        public String getFinalUrl() { return finalUrl; }
        public String getNormalizedUrl() { return normalizedUrl; }
        public int getStatusCode() { return statusCode; }
    }
}
