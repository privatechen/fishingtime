package com.fishingtime.pricewatch.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class PriceWatchRecognizeResponse {
    private boolean recognized;
    private boolean timeout;
    private String skuId;
    private BigDecimal price;
    private String imageUrl;
    private String message;
}
