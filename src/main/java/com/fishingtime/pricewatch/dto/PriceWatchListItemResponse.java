package com.fishingtime.pricewatch.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class PriceWatchListItemResponse {
    private Long watchId;
    private String platform;
    private String skuId;
    private String itemId;
    private String productUrl;
    private String imageUrl;
    private String title;
    private BigDecimal currentPrice;
    private BigDecimal purchasePrice;
    private BigDecimal firstPrice;
    private LocalDateTime firstCheckedAt;
    private BigDecimal lowestPrice;
    private LocalDateTime lowestPriceAt;
    private Integer lowPriceEventCount;
    private List<PriceLowEventResponse> lowPriceEvents;
    private Integer watchDays;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private Integer status;
    private Integer productStatus;
    private Boolean available;
}
