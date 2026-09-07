package com.fishingtime.pricewatch.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class PriceWatchCreateRequest {
    private String productUrl;
    private BigDecimal purchasePrice;
    private Integer watchDays;
}
