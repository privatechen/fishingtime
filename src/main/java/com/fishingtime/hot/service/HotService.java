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
        refreshCrawlers(crawlers, "启动初始化", 0);
    }

    @Scheduled(fixedDelay = 600_000)
    public void scheduledRefresh() {
        log.info("[热榜] 定时刷新开始");
        // 常规 10 分钟刷新只刷非限流平台（抖音等有额度限制的走独立调度）
        CrawlerSelection selection = selectCrawlers(false);
        refreshCrawlers(selection.targets(), "定时刷新", selection.failures());
    }

    /** 独立限流调度：只刷新 quotaLimited 平台（抖音热榜，2-6 点 30min / 其余 15min） */
    public void refreshQuotaLimited() {
        log.info("[热榜] 限流平台刷新开始");
        CrawlerSelection selection = selectCrawlers(true);
        refreshCrawlers(selection.targets(), "限流平台刷新", selection.failures());
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

    /** 限流平台刷新间隔（分钟）：凌晨 2-6 点 30min，其余 15min */
    private int intervalFor(int hour) {
        return (hour >= 2 && hour < 6) ? 30 : 15;
    }

    private void refreshCrawlers(List<HotCrawler> targets, String taskName, int initialFailures) {
        int succeeded = 0;
        int failed = initialFailures;
        long startedAt = System.currentTimeMillis();

        for (HotCrawler crawler : targets) {
            if (refresh(crawler)) {
                succeeded++;
            } else {
                failed++;
            }
        }

        long elapsedMs = System.currentTimeMillis() - startedAt;
        if (failed > 0) {
            log.error("[热榜] {}结束，成功={}，失败={}，总耗时={}ms；失败平台保留旧缓存",
                    taskName, succeeded, failed, elapsedMs);
        } else {
            log.info("[热榜] {}结束，成功={}，失败=0，总耗时={}ms",
                    taskName, succeeded, elapsedMs);
        }
    }

    private CrawlerSelection selectCrawlers(boolean quotaLimited) {
        List<HotCrawler> targets = new java.util.ArrayList<>();
        int failures = 0;
        for (HotCrawler crawler : crawlers) {
            try {
                if (crawler.quotaLimited() == quotaLimited) {
                    targets.add(crawler);
                }
            } catch (Exception e) {
                failures++;
                log.error("[热榜] 读取抓取器限流配置失败，跳过本轮刷新，抓取器={}，原因={}",
                        crawler.getClass().getName(), e.getMessage(), e);
            }
        }
        return new CrawlerSelection(targets, failures);
    }

    private record CrawlerSelection(List<HotCrawler> targets, int failures) {}

    private boolean refresh(HotCrawler crawler) {
        long startedAt = System.currentTimeMillis();
        try {
            String platform = crawler.platform();
            boolean quotaLimited = crawler.quotaLimited();
            List<HotItemDTO> data = crawler.fetch();
            if (data != null && !data.isEmpty()) {
                String now = LocalDateTime.now().toString();
                // 限流平台（抖音）按时间窗口算下次刷新；其余固定 10 分钟
                int nextMinutes = quotaLimited ? intervalFor(LocalDateTime.now().getHour()) : 10;
                cache.put(platform, data);
                updateTimeMap.put(platform, now);
                nextRefreshTimeMap.put(platform, LocalDateTime.now().plusMinutes(nextMinutes).toString());
                log.info("[热榜] {} 刷新成功，{} 条，下次 {}min 后", platform, data.size(), nextMinutes);
                return true;
            } else {
                log.error("[热榜] {} 抓取失败：返回空结果，耗时={}ms，保留旧缓存",
                        platform, System.currentTimeMillis() - startedAt);
                return false;
            }
        } catch (Exception e) {
            log.error("[热榜] 抓取器执行失败：抓取器={}，耗时={}ms，保留旧缓存，原因={}",
                    crawler.getClass().getName(), System.currentTimeMillis() - startedAt, e.getMessage(), e);
            return false;
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
