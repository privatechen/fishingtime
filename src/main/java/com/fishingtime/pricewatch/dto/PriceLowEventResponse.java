package com.fishingtime.pricewatch.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class PriceLowEventResponse {
    private LocalDateTime checkedAt;
    private BigDecimal price;
    private BigDecimal difference;
}
