package com.fishingtime.hot.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HotClusterServiceTest {

    @Mock
    private HotService hotService;
    @Mock
    private HotSimilarityService hotSimilarityService;
    @Mock
    private CommonHotRefiner commonHotRefiner;

    @Test
    void shouldCacheClusterResultWithinTtl() {
        when(hotService.getAllHotSnapshot()).thenReturn(Map.of());
        when(hotSimilarityService.cluster(any())).thenReturn(List.of());
        when(commonHotRefiner.refine(anyList(), any())).thenReturn(List.of());

        HotClusterService service = new HotClusterService(hotService, hotSimilarityService, commonHotRefiner);

        service.getClusters();
        service.getClusters();

        // 第二次调用命中缓存，聚类与过滤只执行一次
        verify(hotSimilarityService, times(1)).cluster(any());
        verify(commonHotRefiner, times(1)).refine(anyList(), any());
        assertEquals(1, service.cachedEntryCount());
    }

    @Test
    void shouldRecomputeAfterCacheClear() {
        when(hotService.getAllHotSnapshot()).thenReturn(Map.of());
        when(hotSimilarityService.cluster(any())).thenReturn(List.of());
        when(commonHotRefiner.refine(anyList(), any())).thenReturn(List.of());

        HotClusterService service = new HotClusterService(hotService, hotSimilarityService, commonHotRefiner);

        service.getClusters();
        service.clearCache();
        assertEquals(0, service.cachedEntryCount());
        service.getClusters();

        verify(hotSimilarityService, times(2)).cluster(any());
    }

    @Test
    void shouldReturnEmptyForEmptySnapshot() {
        // 真实依赖链路（无 Spring）：空快照 → 无簇，验证 getClusters 可用无参链路跑通
        HotClusterService service = new HotClusterService(
                new HotService(List.of()),
                new HotSimilarityService(),
                new CommonHotRefiner());

        assertTrue(service.getClusters().isEmpty());
    }
}
