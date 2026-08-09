package com.fishingtime.hot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 跨平台同类热点聚合结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SimilarHotClusterDTO {

    /** 聚合展示标题，直接选簇内热度最高的一条，避免额外生成标题。 */
    private String title;

    /** 命中的不同平台数量。 */
    private Integer sourceCount;

    /** 最多返回 3 条，且每个平台最多一条。 */
    private List<PlatformHotItemDTO> items;
}
