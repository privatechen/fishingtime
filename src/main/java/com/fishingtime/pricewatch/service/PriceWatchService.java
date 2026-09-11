package com.fishingtime.pricewatch.service;

import com.fishingtime.common.dto.ErrorCode;
import com.fishingtime.common.exception.BusinessException;
import com.fishingtime.pricewatch.dto.PriceHistoryPointResponse;
import com.fishingtime.pricewatch.dto.PriceWatchCreateRequest;
import com.fishingtime.pricewatch.dto.PriceWatchCreateResponse;
import com.fishingtime.pricewatch.dto.PriceWatchListItemResponse;
import com.fishingtime.pricewatch.dto.PriceguardPriceWatchCreateRequest;
import com.fishingtime.pricewatch.mapper.JdProductMapper;
import com.fishingtime.pricewatch.mapper.PriceHistoryMapper;
import com.fishingtime.pricewatch.mapper.PriceWatchMapper;
import com.fishingtime.pricewatch.mapper.TaobaoProductMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PriceWatchService {

    private static final Pattern JD_ITEM_PATH = Pattern.compile("^/(\\d+)\\.html/?$");
    private static final Pattern DIGITS = Pattern.compile("^\\d+$");
    private static final Pattern URL_PATTERN = Pattern.compile("https?://[^\\s\\u3000\\\"'<>]+", Pattern.CASE_INSENSITIVE);

    private final JdProductMapper jdProductMapper;
    private final TaobaoProductMapper taobaoProductMapper;
    private final PriceWatchMapper priceWatchMapper;
    private final PriceHistoryMapper priceHistoryMapper;
    private final JdShortLinkResolver jdShortLinkResolver;
    private final TaobaoShortLinkPriceClient taobaoShortLinkPriceClient;

    @Transactional
    public PriceWatchCreateResponse create(Long userId, PriceWatchCreateRequest request) {
        if (userId == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);
        if (request == null) throw new BusinessException(ErrorCode.PARAM_INVALID, "请求参数不能为空");

        ParsedProduct parsed = parseProduct(request.getProductUrl());
        BigDecimal purchasePrice = request.getPurchasePrice();
        if (purchasePrice == null || purchasePrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "购买价必须大于 0");
        }
        Integer watchDays = request.getWatchDays();
        if (watchDays == null || (watchDays != 7 && watchDays != 15 && watchDays != 30)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "监控天数仅支持 7、15 或 30 天");
        }

        Long productId;
        if ("JD".equals(parsed.platform())) {
            productId = jdProductMapper.findIdBySkuId(parsed.productId());
            if (productId == null) {
                jdProductMapper.insertIgnore(parsed.productId(), parsed.productUrl());
                productId = jdProductMapper.findIdBySkuId(parsed.productId());
            }
        } else {
            productId = taobaoProductMapper.findIdByItemId(parsed.productId());
            if (productId == null) {
                taobaoProductMapper.insertIgnore(parsed.productId(), parsed.productUrl());
                productId = taobaoProductMapper.findIdByItemId(parsed.productId());
            }
        }
        if (productId == null) throw new BusinessException(ErrorCode.SYSTEM_ERROR, "保存商品失败");

        LocalDateTime startAt = LocalDateTime.now();
        LocalDateTime endAt = startAt.plusDays(watchDays);
        PriceWatchMapper.PriceWatchInsertParam param = new PriceWatchMapper.PriceWatchInsertParam();
        param.setUserId(userId);
        param.setPlatform(parsed.platform());
        param.setProductId(productId);
        param.setPurchasePrice(purchasePrice);
        param.setStartAt(startAt);
        param.setEndAt(endAt);
        priceWatchMapper.insert(param);

        return PriceWatchCreateResponse.builder()
                .watchId(param.getId())
                .platform(parsed.platform())
                .skuId("JD".equals(parsed.platform()) ? parsed.productId() : null)
                .itemId("TAOBAO".equals(parsed.platform()) ? parsed.productId() : null)
                .productUrl(parsed.productUrl())
                .purchasePrice(purchasePrice)
                .watchDays(watchDays)
                .startAt(startAt)
                .endAt(endAt)
                .build();
    }

    /**
     * PriceGuard only submits a Taobao share text. Fishingtime extracts and stores
     * the m.tb.cn URL; PriceTool will process the database row asynchronously.
     */
    @Transactional
    public PriceWatchCreateResponse createFromPriceguard(Long userId, PriceguardPriceWatchCreateRequest request) {
        int textLength = request == null || request.getProductText() == null
                ? 0 : request.getProductText().length();
        log.info("[PriceGuard盯价] 请求进入 userId={}, productTextLength={}, purchasePrice={}, watchDays={}",
                userId, textLength, request == null ? null : request.getPurchasePrice(),
                request == null ? null : request.getWatchDays());

        try {
            if (userId == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);
            if (request == null) throw new BusinessException(ErrorCode.PARAM_INVALID, "请求参数不能为空");

            String shortUrl = extractTaobaoShortUrl(request.getProductText());
            log.info("[PriceGuard盯价] 淘宝短链提取成功 userId={}, shortUrl={}", userId, shortUrl);

            BigDecimal purchasePrice = request.getPurchasePrice();
            if (purchasePrice == null || purchasePrice.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "购买价必须大于 0");
            }
            Integer watchDays = request.getWatchDays();
            if (watchDays == null || watchDays < 1 || watchDays > 30) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "监控天数仅支持 1 到 30 天");
            }

            Long productId = taobaoProductMapper.findIdByProductUrl(shortUrl);
            if (productId == null) {
                log.info("[PriceGuard盯价] 商品不存在，准备写入 taobao_product userId={}, shortUrl={}",
                        userId, shortUrl);
                int inserted = taobaoProductMapper.insertPending(shortUrl);
                log.info("[PriceGuard盯价] taobao_product 写入完成 userId={}, affectedRows={}, shortUrl={}",
                        userId, inserted, shortUrl);
                productId = taobaoProductMapper.findIdByProductUrl(shortUrl);
            } else {
                log.info("[PriceGuard盯价] 复用已有淘宝商品 userId={}, productId={}, shortUrl={}",
                        userId, productId, shortUrl);
            }
            if (productId == null) {
                log.error("[PriceGuard盯价] 写入后未查询到商品 userId={}, shortUrl={}", userId, shortUrl);
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "保存淘宝短链接失败");
            }

            LocalDateTime startAt = LocalDateTime.now();
            LocalDateTime endAt = startAt.plusDays(watchDays);
            PriceWatchMapper.PriceWatchInsertParam param = new PriceWatchMapper.PriceWatchInsertParam();
            param.setUserId(userId);
            param.setPlatform("TAOBAO");
            param.setProductId(productId);
            param.setPurchasePrice(purchasePrice);
            param.setStartAt(startAt);
            param.setEndAt(endAt);
            int inserted = priceWatchMapper.insert(param);
            log.info("[PriceGuard盯价] 监控记录写入成功 userId={}, productId={}, watchId={}, affectedRows={}, endAt={}",
                    userId, productId, param.getId(), inserted, endAt);

            return PriceWatchCreateResponse.builder()
                    .watchId(param.getId())
                    .platform("TAOBAO")
                    .productUrl(shortUrl)
                    .purchasePrice(purchasePrice)
                    .watchDays(watchDays)
                    .startAt(startAt)
                    .endAt(endAt)
                    .build();
        } catch (BusinessException e) {
            log.warn("[PriceGuard盯价] 业务失败 userId={}, message={}", userId, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("[PriceGuard盯价] 系统异常 userId={}, productTextLength={}", userId, textLength, e);
            throw e;
        }
    }

    private String extractTaobaoShortUrl(String productText) {
        if (productText == null || productText.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "商品分享内容不能为空");
        }
        Matcher matcher = URL_PATTERN.matcher(productText);
        while (matcher.find()) {
            String value = matcher.group().replaceAll("[，。！？；：、）》】」』]+$", "");
            try {
                URI uri = URI.create(value);
                if ("m.tb.cn".equalsIgnoreCase(uri.getHost())) return value;
            } catch (Exception ignored) {
                // Continue looking for another URL in the share text.
            }
        }
        throw new BusinessException(ErrorCode.PARAM_INVALID, "未找到淘宝 m.tb.cn 短链接");
    }

    public List<PriceWatchListItemResponse> list(Long userId) {
        if (userId == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);
        return priceWatchMapper.findByUserId(userId).stream().map(row -> {
            boolean jd = "JD".equals(row.getPlatform());
            return PriceWatchListItemResponse.builder()
                    .watchId(row.getWatchId())
                    .platform(row.getPlatform())
                    .skuId(jd ? row.getPlatformProductId() : null)
                    .itemId(jd ? null : row.getPlatformProductId())
                    .productUrl(row.getProductUrl())
                    .imageUrl(row.getImageUrl())
                    .title(row.getTitle())
                    .currentPrice(row.getCurrentPrice())
                    .purchasePrice(row.getPurchasePrice())
                    .watchDays(calculateWatchDays(row.getStartAt(), row.getEndAt()))
                    .startAt(row.getStartAt())
                    .endAt(row.getEndAt())
                    .status(row.getStatus())
                    .productStatus(row.getProductStatus())
                    .available(Integer.valueOf(1).equals(row.getProductStatus()))
                    .build();
        }).collect(Collectors.toList());
    }

    public List<PriceHistoryPointResponse> history(Long userId, Long watchId, Integer days) {
        if (userId == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);
        if (watchId == null || watchId <= 0) throw new BusinessException(ErrorCode.PARAM_INVALID, "监控记录不能为空");
        int queryDays = days == null ? 30 : days;
        if (queryDays != 7 && queryDays != 30 && queryDays != 90) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "历史报价仅支持 7、30 或 90 天");
        }
        PriceWatchMapper.PriceWatchTarget target = priceWatchMapper.findTargetByWatchAndUser(watchId, userId);
        if (target == null || target.getProductId() == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "监控记录不存在");
        }
        return priceHistoryMapper.findDailyLastPrices(target.getPlatform(), target.getProductId(), queryDays).stream()
                .map(row -> PriceHistoryPointResponse.builder().date(row.getPriceDate()).price(row.getPrice()).build())
                .collect(Collectors.toList());
    }

    @Transactional
    public void remove(Long userId, Long watchId) {
        if (userId == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);
        if (watchId == null || watchId <= 0) throw new BusinessException(ErrorCode.PARAM_INVALID, "监控记录不能为空");
        int updated = priceWatchMapper.disableByUser(watchId, userId);
        if (updated <= 0) throw new BusinessException(ErrorCode.PARAM_INVALID, "监控记录不存在或已移除");
    }

    private int calculateWatchDays(LocalDateTime startAt, LocalDateTime endAt) {
        if (startAt == null || endAt == null) return 0;
        return (int) ChronoUnit.DAYS.between(startAt, endAt);
    }

    private ParsedProduct parseProduct(String productUrl) {
        if (productUrl == null || productUrl.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "商品链接不能为空");
        }
        String input = productUrl.trim();
        if (input.contains("3.cn/")) {
            JdShortLinkResolver.ResolveResult resolved = jdShortLinkResolver.resolve(input);
            return new ParsedProduct("JD", resolved.getItemId(), resolved.getNormalizedUrl());
        }

        // Fishingtime is only the entry point. Pass the complete Taobao/Tmall
        // sharing text to PriceTool; URL extraction and short-link resolution belong there.
        if (input.toLowerCase().contains("m.tb.cn/")) {
            TaobaoShortLinkPriceClient.ResolveResult resolved = taobaoShortLinkPriceClient.resolveAndCollect(input);
            return new ParsedProduct("TAOBAO", resolved.getItemId(), resolved.getShortUrl());
        }

        try {
            URI uri = URI.create(input);
            if (uri.getScheme() == null || !("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme())) || uri.getHost() == null) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "商品链接格式不正确");
            }
            String host = uri.getHost().toLowerCase();
            if ("item.jd.com".equals(host)) {
                Matcher matcher = JD_ITEM_PATH.matcher(uri.getPath());
                if (!matcher.matches()) throw new BusinessException(ErrorCode.PARAM_INVALID, "无法从京东商品链接中识别 SKU");
                String skuId = matcher.group(1);
                return new ParsedProduct("JD", skuId, "https://item.jd.com/" + skuId + ".html");
            }
            if (host.endsWith("taobao.com") || host.endsWith("tmall.com")) {
                String itemId = queryParam(uri.getRawQuery(), "id");
                if (itemId == null || !DIGITS.matcher(itemId).matches()) {
                    throw new BusinessException(ErrorCode.PARAM_INVALID, "无法从淘宝/天猫商品链接中识别商品 ID");
                }
                return new ParsedProduct("TAOBAO", itemId, input);
            }
            throw new BusinessException(ErrorCode.PARAM_INVALID, "当前仅支持京东、淘宝和天猫商品链接");
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "商品链接格式不正确");
        }
    }

    private String queryParam(String rawQuery, String name) {
        if (rawQuery == null || rawQuery.isBlank()) return null;
        for (String part : rawQuery.split("&")) {
            int equals = part.indexOf('=');
            String rawKey = equals >= 0 ? part.substring(0, equals) : part;
            String key = URLDecoder.decode(rawKey, StandardCharsets.UTF_8);
            if (!name.equals(key)) continue;
            String rawValue = equals >= 0 ? part.substring(equals + 1) : "";
            return URLDecoder.decode(rawValue, StandardCharsets.UTF_8);
        }
        return null;
    }

    private static final class ParsedProduct {
        private final String platform;
        private final String productId;
        private final String productUrl;

        private ParsedProduct(String platform, String productId, String productUrl) {
            this.platform = platform;
            this.productId = productId;
            this.productUrl = productUrl;
        }

        private String platform() { return platform; }
        private String productId() { return productId; }
        private String productUrl() { return productUrl; }
    }
}
