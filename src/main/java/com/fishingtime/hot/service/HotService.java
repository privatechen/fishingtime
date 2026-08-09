package com.fishingtime.hot.service;

import com.fishingtime.hot.crawler.HotCrawler;
import com.fishingtime.hot.dto.HotItemDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 热榜服务 — 缓存管理 + 定时刷新
 *
 * 记录每次刷新的 updateTime 和 nextRefreshTime，
 * 供前端缓存有效期判断。
 */
@Slf4j
@Service
public class HotService {

    /** 缓存: platform → 最近一次成功抓取的热榜数据 */
    private final Map<String, List<HotItemDTO>> cache = new ConcurrentHashMap<>();

    /** 刷新时间: platform → 本次刷新完成时间 */
    private final Map<String, String> updateTimeMap = new ConcurrentHashMap<>();

    /** 下次刷新时间: platform → 预计下次刷新时间（updateTime + 10min） */
    private final Map<String, String> nextRefreshTimeMap = new ConcurrentHashMap<>();

    private final List<HotCrawler> crawlers;

    public HotService(List<HotCrawler> crawlers) {
        this.crawlers = crawlers;
    }

    @PostConstruct
    public void init() {
        log.info("[热榜] 启动初始化，共 {} 个平台", crawlers.size());
        crawlers.forEach(this::refresh);
    }

    @Scheduled(fixedDelay = 600_000)
    public void scheduledRefresh() {
        log.info("[热榜] 定时刷新开始");
        crawlers.forEach(this::refresh);
    }

    /** 获取某平台的热榜数据及刷新时间。 */
    public HotResult getHot(String platform) {
        return new HotResult(
                cache.getOrDefault(platform, List.of()),
                updateTimeMap.get(platform),
                nextRefreshTimeMap.get(platform)
        );
    }

    /**
     * 返回当前所有平台热榜缓存的快照，供跨平台相似热点计算使用。
     * 返回副本，避免相似度服务修改缓存 Map 本身。
     */
    public Map<String, List<HotItemDTO>> getAllHotSnapshot() {
        return new HashMap<>(cache);
    }

    private void refresh(HotCrawler crawler) {
        String platform = crawler.platform();
        try {
            List<HotItemDTO> data = crawler.fetch();
            if (data != null && !data.isEmpty()) {
                cache.put(platform, data);
                String now = LocalDateTime.now().toString();
                updateTimeMap.put(platform, now);
                nextRefreshTimeMap.put(platform, LocalDateTime.now().plusMinutes(10).toString());
                log.info("[热榜] {} 刷新成功，{} 条", platform, data.size());
            } else {
                log.warn("[热榜] {} 刷新结果为空，保留旧缓存", platform);
            }
        } catch (Exception e) {
            log.error("[热榜] {} 刷新异常: {}", platform, e.getMessage());
        }
    }

    /** 热榜查询结果 — 包含数据及刷新时间。 */
    public static class HotResult {
        private final List<HotItemDTO> data;
        private final String updateTime;
        private final String nextRefreshTime;

        public HotResult(List<HotItemDTO> data, String updateTime, String nextRefreshTime) {
            this.data = data;
            this.updateTime = updateTime;
            this.nextRefreshTime = nextRefreshTime;
        }

        public List<HotItemDTO> getData() { return data; }
        public String getUpdateTime() { return updateTime; }
        public String getNextRefreshTime() { return nextRefreshTime; }
    }
}
