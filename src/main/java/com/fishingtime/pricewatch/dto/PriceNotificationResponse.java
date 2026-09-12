package com.fishingtime.pricewatch.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class PriceNotificationResponse {
    private Long id;
    private Long watchId;
    private String type;
    private String productTitle;
    private BigDecimal currentPrice;
    private BigDecimal comparisonPrice;
    private BigDecimal dropAmount;
    private LocalDateTime createdAt;
}
