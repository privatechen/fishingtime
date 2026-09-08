package com.fishingtime.pricewatch.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fishingtime.common.dto.ErrorCode;
import com.fishingtime.common.exception.BusinessException;
import com.fishingtime.pricewatch.dto.PriceWatchCreateRequest;
import com.fishingtime.pricewatch.dto.PriceWatchCreateResponse;
import com.fishingtime.pricewatch.dto.PriceWatchRecognizeResponse;
import com.fishingtime.pricewatch.mapper.JdProductMapper;
import com.fishingtime.pricewatch.mapper.PriceWatchMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class PriceWatchService {

    private static final Logger log = LoggerFactory.getLogger(PriceWatchService.class);
    private static final Pattern JD_ITEM_PATH = Pattern.compile("^/(\\d+)\\.html/?$");
    private static final Duration PRICE_TOOL_TIMEOUT = Duration.ofSeconds(5);
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();

    private final JdProductMapper jdProductMapper;
    private final PriceWatchMapper priceWatchMapper;
    private final ObjectMapper objectMapper;

    @Value("${price-watch.price-tool.endpoint-8765:http://39.96.214.41:8765/api/jd/price}")
    private String priceTool8765;

    @Value("${price-watch.price-tool.endpoint-8766:http://39.96.214.41:8766/api/jd/price}")
    private String priceTool8766;

    public PriceWatchRecognizeResponse recognize(String productUrl) {
        String skuId = parseJdSku(productUrl);
        String normalizedUrl = "https://item.jd.com/" + skuId + ".html";
        long startedAt = System.currentTimeMillis();

        log.info("Price watch recognition started. skuId={}", skuId);

        CompletableFuture<PriceToolResult> request8765 = requestPriceTool(priceTool8765, normalizedUrl, skuId);
        CompletableFuture<PriceToolResult> request8766 = requestPriceTool(priceTool8766, normalizedUrl, skuId);
        List<CompletableFuture<PriceToolResult>> requests = new ArrayList<>();
        requests.add(request8765);
        requests.add(request8766);

        CompletableFuture<PriceToolResult> firstUsefulResult = new CompletableFuture<>();
        for (CompletableFuture<PriceToolResult> request : requests) {
            request.whenComplete((result, error) -> {
                if (error != null) {
                    log.debug("PriceTool request failed. skuId={}, message={}", skuId, error.getMessage());
                    return;
                }
                if (isUseful(result)) {
                    firstUsefulResult.complete(result);
                }
            });
        }

        try {
            PriceToolResult result = firstUsefulResult.get(PRICE_TOOL_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            long elapsedMs = System.currentTimeMillis() - startedAt;
            log.info("Price watch recognition succeeded. skuId={}, price={}, imageUrlPresent={}, elapsedMs={}",
                    skuId, result.price, hasText(result.imageUrl), elapsedMs);

            return PriceWatchRecognizeResponse.builder()
                    .recognized(true)
                    .timeout(false)
                    .skuId(skuId)
                    .price(result.price)
                    .imageUrl(result.imageUrl)
                    .message("商品信息识别成功")
                    .build();
        } catch (TimeoutException e) {
            long elapsedMs = System.currentTimeMillis() - startedAt;
            log.warn("Price watch recognition timed out. skuId={}, elapsedMs={}", skuId, elapsedMs);
            return timeoutResponse(skuId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Price watch recognition interrupted. skuId={}", skuId);
            return timeoutResponse(skuId);
        } catch (Exception e) {
            log.warn("Price watch recognition failed. skuId={}, message={}", skuId, e.getMessage());
            return timeoutResponse(skuId);
        } finally {
            for (CompletableFuture<PriceToolResult> request : requests) {
                if (!request.isDone()) {
                    request.cancel(true);
                }
            }
        }
    }

    @Transactional
    public PriceWatchCreateResponse create(Long userId, PriceWatchCreateRequest request) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "请求参数不能为空");
        }

        String skuId = parseJdSku(request.getProductUrl());
        BigDecimal purchasePrice = request.getPurchasePrice();
        if (purchasePrice == null || purchasePrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "购买价必须大于 0");
        }

        Integer watchDays = request.getWatchDays();
        if (watchDays == null || (watchDays != 7 && watchDays != 15 && watchDays != 30)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "监控天数仅支持 7、15 或 30 天");
        }

        String normalizedUrl = "https://item.jd.com/" + skuId + ".html";
        Long productId = jdProductMapper.findIdBySkuId(skuId);
        if (productId == null) {
            jdProductMapper.insertIgnore(skuId, normalizedUrl);
            productId = jdProductMapper.findIdBySkuId(skuId);
        }
        if (productId == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "保存京东商品失败");
        }

        LocalDateTime startAt = LocalDateTime.now();
        LocalDateTime endAt = startAt.plusDays(watchDays);

        PriceWatchMapper.PriceWatchInsertParam param = new PriceWatchMapper.PriceWatchInsertParam();
        param.setUserId(userId);
        param.setProductId(productId);
        param.setPurchasePrice(purchasePrice);
        param.setStartAt(startAt);
        param.setEndAt(endAt);
        priceWatchMapper.insert(param);

        return PriceWatchCreateResponse.builder()
                .watchId(param.getId())
                .platform("JD")
                .skuId(skuId)
                .productUrl(normalizedUrl)
                .purchasePrice(purchasePrice)
                .watchDays(watchDays)
                .startAt(startAt)
                .endAt(endAt)
                .build();
    }

    private CompletableFuture<PriceToolResult> requestPriceTool(String endpoint, String productUrl, String skuId) {
        final String requestBody;
        try {
            requestBody = objectMapper.writeValueAsString(Collections.singletonMap("url", productUrl));
        } catch (Exception e) {
            CompletableFuture<PriceToolResult> failed = new CompletableFuture<>();
            failed.completeExceptionally(e);
            return failed;
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .timeout(PRICE_TOOL_TIMEOUT)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        return HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> parsePriceToolResponse(endpoint, skuId, response));
    }

    private PriceToolResult parsePriceToolResponse(String endpoint,
                                                    String skuId,
                                                    HttpResponse<String> response) {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            log.debug("PriceTool returned non-success HTTP status. skuId={}, endpoint={}, status={}",
                    skuId, endpoint, response.statusCode());
            return null;
        }

        try {
            JsonNode root = objectMapper.readTree(response.body());
            BigDecimal price = parsePrice(root.get("price"));
            String imageUrl = textValue(root.get("imageUrl"));
            if (!hasText(imageUrl)) {
                // Keep compatibility if the upstream field is named url.
                imageUrl = textValue(root.get("url"));
            }

            PriceToolResult result = new PriceToolResult(price, imageUrl);
            if (!isUseful(result)) {
                log.debug("PriceTool response did not contain price or image URL. skuId={}, endpoint={}", skuId, endpoint);
            }
            return result;
        } catch (Exception e) {
            log.debug("Failed to parse PriceTool response. skuId={}, endpoint={}, message={}",
                    skuId, endpoint, e.getMessage());
            return null;
        }
    }

    private boolean isUseful(PriceToolResult result) {
        return result != null && (result.price != null || hasText(result.imageUrl));
    }

    private BigDecimal parsePrice(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        try {
            BigDecimal price = new BigDecimal(node.asText().replace(",", "").trim());
            return price.compareTo(BigDecimal.ZERO) > 0 ? price : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private String textValue(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        String value = node.asText();
        return hasText(value) ? value.trim() : null;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private PriceWatchRecognizeResponse timeoutResponse(String skuId) {
        return PriceWatchRecognizeResponse.builder()
                .recognized(false)
                .timeout(true)
                .skuId(skuId)
                .price(null)
                .imageUrl(null)
                .message("商品信息识别超时，可继续填写购买价格和监控时间")
                .build();
    }

    private String parseJdSku(String productUrl) {
        if (productUrl == null || productUrl.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "商品链接不能为空");
        }
        try {
            URI uri = URI.create(productUrl.trim());
            if (uri.getScheme() == null ||
                    !("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme())) ||
                    uri.getHost() == null || !"item.jd.com".equalsIgnoreCase(uri.getHost())) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "当前仅支持 item.jd.com 京东商品链接");
            }
            Matcher matcher = JD_ITEM_PATH.matcher(uri.getPath());
            if (!matcher.matches()) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "无法从京东商品链接中识别 SKU");
            }
            return matcher.group(1);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "京东商品链接格式不正确");
        }
    }

    private static final class PriceToolResult {
        private final BigDecimal price;
        private final String imageUrl;

        private PriceToolResult(BigDecimal price, String imageUrl) {
            this.price = price;
            this.imageUrl = imageUrl;
        }
    }
}
