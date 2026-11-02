package com.fishingtime.hot.service;

import com.fishingtime.hot.crawler.HotCrawler;
import com.fishingtime.hot.dto.HotItemDTO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HotServiceTest {

    @Test
    void continuesRefreshingOtherPlatformsWhenOneCrawlerFails() {
        HotCrawler failing = mock(HotCrawler.class);
        when(failing.platform()).thenReturn("failing");
        when(failing.fetch()).thenThrow(new IllegalStateException("simulated failure"));

        HotCrawler healthy = mock(HotCrawler.class);
        when(healthy.platform()).thenReturn("healthy");
        when(healthy.fetch()).thenReturn(List.of(item("new topic")));

        HotService service = new HotService(List.of(failing, healthy));
        service.scheduledRefresh();

        verify(failing).fetch();
        verify(healthy).fetch();
        assertEquals("new topic", service.getHot("healthy").getData().get(0).getTitle());
    }

    @Test
    void keepsLastSuccessfulCacheWhenLaterRefreshIsEmpty() {
        HotCrawler crawler = mock(HotCrawler.class);
        when(crawler.platform()).thenReturn("stable");
        when(crawler.fetch())
                .thenReturn(List.of(item("last successful topic")))
                .thenReturn(List.of());

        HotService service = new HotService(List.of(crawler));
        service.init();
        service.scheduledRefresh();

        assertEquals("last successful topic", service.getHot("stable").getData().get(0).getTitle());
    }

    @Test
    void continuesWhenCrawlerMetadataFails() {
        HotCrawler brokenMetadata = mock(HotCrawler.class);
        when(brokenMetadata.platform()).thenThrow(new IllegalStateException("simulated metadata failure"));

        HotCrawler healthy = mock(HotCrawler.class);
        when(healthy.platform()).thenReturn("healthy");
        when(healthy.fetch()).thenReturn(List.of(item("new topic")));

        HotService service = new HotService(List.of(brokenMetadata, healthy));
        service.init();

        verify(healthy).fetch();
        assertEquals("new topic", service.getHot("healthy").getData().get(0).getTitle());
    }

    @Test
    void skipsBrokenQuotaMetadataWithoutBlockingOtherPlatforms() {
        HotCrawler brokenMetadata = mock(HotCrawler.class);
        when(brokenMetadata.quotaLimited()).thenThrow(new IllegalStateException("simulated quota failure"));

        HotCrawler healthy = mock(HotCrawler.class);
        when(healthy.platform()).thenReturn("healthy");
        when(healthy.fetch()).thenReturn(List.of(item("new topic")));

        HotService service = new HotService(List.of(brokenMetadata, healthy));
        service.scheduledRefresh();

        verify(healthy).fetch();
        assertEquals("new topic", service.getHot("healthy").getData().get(0).getTitle());
    }

    private HotItemDTO item(String title) {
        return HotItemDTO.builder()
                .rank(1)
                .title(title)
                .build();
    }
}
