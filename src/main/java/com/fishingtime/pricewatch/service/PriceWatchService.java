package com.fishingtime.pricewatch.service;

import com.fishingtime.common.dto.ErrorCode;
import com.fishingtime.common.exception.BusinessException;
import com.fishingtime.pricewatch.dto.PriceWatchCreateRequest;
import com.fishingtime.pricewatch.dto.PriceWatchCreateResponse;
import com.fishingtime.pricewatch.dto.PriceWatchListItemResponse;
import com.fishingtime.pricewatch.mapper.JdProductMapper;
import com.fishingtime.pricewatch.mapper.PriceWatchMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PriceWatchService {

    private static final Pattern JD_ITEM_PATH = Pattern.compile("^/(\\d+)\\.html/?$");

    private final JdProductMapper jdProductMapper;
    private final PriceWatchMapper priceWatchMapper;

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

    public List<PriceWatchListItemResponse> list(Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        return priceWatchMapper.findByUserId(userId).stream()
                .map(row -> PriceWatchListItemResponse.builder()
                        .watchId(row.getWatchId())
                        .platform(row.getPlatform())
                        .skuId(row.getSkuId())
                        .productUrl(row.getProductUrl())
                        .imageUrl(row.getImageUrl())
                        .purchasePrice(row.getPurchasePrice())
                        .watchDays(calculateWatchDays(row.getStartAt(), row.getEndAt()))
                        .startAt(row.getStartAt())
                        .endAt(row.getEndAt())
                        .status(row.getStatus())
                        .productStatus(row.getProductStatus())
                        .available(Integer.valueOf(1).equals(row.getProductStatus()))
                        .build())
                .collect(Collectors.toList());
    }

    private int calculateWatchDays(LocalDateTime startAt, LocalDateTime endAt) {
        if (startAt == null || endAt == null) {
            return 0;
        }
        return (int) ChronoUnit.DAYS.between(startAt, endAt);
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
}
