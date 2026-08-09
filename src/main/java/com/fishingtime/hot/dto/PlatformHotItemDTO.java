package com.fishingtime.hot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 带平台信息的热榜条目，用于跨平台热点匹配。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlatformHotItemDTO {
    private String platform;
    private HotItemDTO hotItem;
    private Double similarityScore;
}
