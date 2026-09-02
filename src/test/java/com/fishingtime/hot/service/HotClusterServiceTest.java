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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HotClusterServiceTest {

    @Mock
    private HotService hotService;
    @Mock
    private EventFingerprintClusterService eventFingerprintClusterService;
    @Mock
    private HotSimilarityService hotSimilarityService;
    @Mock
    private CommonHotRefiner commonHotRefiner;

    @Test
    void shouldCacheClusterResultWithinTtl() {
        when(hotService.getAllHotSnapshot()).thenReturn(Map.of());
        when(eventFingerprintClusterService.cluster(any())).thenReturn(List.of());

        HotClusterService service = new HotClusterService(
                hotService,
                eventFingerprintClusterService,
                hotSimilarityService,
                commonHotRefiner);

        service.getClusters();
        service.getClusters();

        verify(eventFingerprintClusterService, times(1)).cluster(any());
        assertEquals(1, service.cachedEntryCount());
    }

    @Test
    void shouldRecomputeAfterCacheClear() {
        when(hotService.getAllHotSnapshot()).thenReturn(Map.of());
        when(eventFingerprintClusterService.cluster(any())).thenReturn(List.of());

        HotClusterService service = new HotClusterService(
                hotService,
                eventFingerprintClusterService,
                hotSimilarityService,
                commonHotRefiner);

        service.getClusters();
        service.clearCache();
        assertEquals(0, service.cachedEntryCount());
        service.getClusters();

        verify(eventFingerprintClusterService, times(2)).cluster(any());
    }

    @Test
    void shouldReturnEmptyForEmptySnapshot() {
        HotClusterService service = new HotClusterService(
                new HotService(List.of()),
                new EventFingerprintClusterService(),
                new HotSimilarityService(),
                new CommonHotRefiner());

        assertTrue(service.getClusters().isEmpty());
    }
}
