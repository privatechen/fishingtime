package com.fishingtime.pricewatch.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class PriceWatchListItemResponse {
    private Long watchId;
    private String platform;
    private String skuId;
    private String productUrl;
    private BigDecimal purchasePrice;
    private Integer watchDays;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private Integer status;
    private Integer productStatus;
    private Boolean available;
}
