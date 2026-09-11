package com.fishingtime.pricewatch.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * Request from priceguard mini program.
 * productText keeps the original share text so backend can reuse the same
 * extraction and short-link resolving logic as fishingtime.
 */
@Data
public class PriceguardPriceWatchCreateRequest {
    private String productText;
    private BigDecimal purchasePrice;
    private Integer watchDays;
}
