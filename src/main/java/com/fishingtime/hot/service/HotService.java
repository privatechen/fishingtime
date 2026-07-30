package com.fishingtime.hot.service;

import com.fishingtime.hot.crawler.HotCrawler;
import com.fishingtime.hot.dto.HotItemDTO;
import javax.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 热榜服务 — 缓存管理 + 定时刷新
 *
 * 通过 List<HotCrawler> 自动收集所有 HotCrawler 实现，
 * 新增平台只需加一个 @Component 实现类，无需改此文件。
 */
@Slf4j
@Service
public class HotService {

    /** 缓存: platform → 最近一次成功抓取的热榜数据 */
    private final Map<String, List<HotItemDTO>> cache = new ConcurrentHashMap<>();

    /** Spring 自动注入所有 HotCrawler 实现 */
    private final List<HotCrawler> crawlers;

    public HotService(List<HotCrawler> crawlers) {
        this.crawlers = crawlers;
    }

    /** 启动时立即抓取所有平台 */
    @PostConstruct
    public void init() {
        log.info("[热榜] 启动初始化，共 {} 个平台", crawlers.size());
        crawlers.forEach(this::refresh);
    }

    /** 每 10 分钟定时刷新所有平台 */
    @Scheduled(fixedDelay = 600_000)
    public void scheduledRefresh() {
        log.info("[热榜] 定时刷新开始");
        crawlers.forEach(this::refresh);
    }

    /**
     * 获取某平台缓存的热榜数据
     *
     * @param platform 平台标识（如 "baidu"）
     * @return 热榜数据，无缓存时返回空列表
     */
    public List<HotItemDTO> getHot(String platform) {
        return cache.getOrDefault(platform, List.of());
    }

    /** 抓取单个平台并更新缓存 */
    private void refresh(HotCrawler crawler) {
        String platform = crawler.platform();
        try {
            List<HotItemDTO> data = crawler.fetch();
            if (data != null && !data.isEmpty()) {
                cache.put(platform, data);
                log.info("[热榜] {} 刷新成功，{} 条", platform, data.size());
            } else {
                log.warn("[热榜] {} 刷新结果为空，保留旧缓存", platform);
            }
        } catch (Exception e) {
            log.error("[热榜] {} 刷新异常: {}", platform, e.getMessage());
            // 保留旧缓存，不抛异常
        }
    }
}
