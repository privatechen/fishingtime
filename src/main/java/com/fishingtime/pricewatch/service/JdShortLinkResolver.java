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
public class JdShortLinkResolver {

    private static final Pattern URL_PATTERN = Pattern.compile("https?://[^\\s]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern ITEM_URL_PATTERN = Pattern.compile("(?:item\\.jd\\.com|item\\.m\\.jd\\.com)/(\\d+)\\.html", Pattern.CASE_INSENSITIVE);
    private static final Pattern SKU_QUERY_PATTERN = Pattern.compile("[?&](?:sku|skuId|sku_id|wareId)=(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern HTML_SKU_PATTERN = Pattern.compile("[\\\"']?(?:skuId|sku_id|wareId)[\\\"']?\\s*[:=]\\s*[\\\"']?(\\d+)", Pattern.CASE_INSENSITIVE);

    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public ResolveResult resolve(String input) {
        String sourceUrl = extractUrl(input);
        validateJdHost(sourceUrl);

        String directSkuId = extractSkuId(sourceUrl);
        if (directSkuId != null) {
            return result(directSkuId, sourceUrl, sourceUrl, 200);
        }

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
            String skuId = extractSkuId(finalUrl);
            if (skuId == null) {
                skuId = extractSkuIdFromHtml(response.body());
            }
            if (skuId == null) {
                throw new BusinessException(ErrorCode.PARAM_INVALID,
                        "京东短链已访问，但没有找到商品ID。finalUrl=" + finalUrl);
            }
            return result(skuId, sourceUrl, finalUrl, response.statusCode());
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "解析京东短链失败: " + e.getMessage());
        }
    }

    private ResolveResult result(String skuId, String sourceUrl, String finalUrl, int statusCode) {
        return new ResolveResult(skuId, sourceUrl, finalUrl,
                "https://item.jd.com/" + skuId + ".html", statusCode);
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

    private void validateJdHost(String url) {
        try {
            String host = URI.create(url).getHost();
            if (host == null) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "无效的京东链接");
            }
            String lower = host.toLowerCase(Locale.ROOT);
            if (!(lower.equals("3.cn") || lower.equals("jd.com") || lower.endsWith(".jd.com"))) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "当前接口仅用于解析京东链接");
            }
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "无效的京东链接");
        }
    }

    private String extractSkuId(String url) {
        if (url == null || url.trim().isEmpty()) return null;
        String decoded = URLDecoder.decode(url, StandardCharsets.UTF_8);
        Matcher itemMatcher = ITEM_URL_PATTERN.matcher(decoded);
        if (itemMatcher.find()) return itemMatcher.group(1);
        Matcher queryMatcher = SKU_QUERY_PATTERN.matcher(decoded);
        return queryMatcher.find() ? queryMatcher.group(1) : null;
    }

    private String extractSkuIdFromHtml(String html) {
        if (html == null || html.trim().isEmpty()) return null;
        String normalized = html.replace("\\u002F", "/").replace("\\u002f", "/").replace("\\/", "/");
        Matcher itemMatcher = ITEM_URL_PATTERN.matcher(normalized);
        if (itemMatcher.find()) return itemMatcher.group(1);
        Matcher skuMatcher = HTML_SKU_PATTERN.matcher(normalized);
        return skuMatcher.find() ? skuMatcher.group(1) : null;
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
