package com.fishingtime.pricewatch.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class PriceWatchSummaryResponse {
    private Integer totalWatchCount;
    private Integer lowPriceEventCount;
    private BigDecimal cumulativeDifference;
}
