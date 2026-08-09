package com.fishingtime.hot.service;

import com.fishingtime.hot.dto.HotItemDTO;
import com.fishingtime.hot.dto.SimilarHotClusterDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 聚类门面服务：聚合"聚类 + 严格过滤"，并对结果做 10 分钟 TTL 缓存。
 *
 * 热榜数据每 10 分钟刷新一次，聚类结果在其间保持稳定；数据刷新后最多滞后一个 TTL 周期，
 * 与平台数据缓存本身可接受的陈旧度一致，故用纯 TTL，不引入刷新主动失效的耦合。
 * 并发：miss 时多线程各自计算 + put，结果确定性、计算廉价，不加锁。
 */
@Service
@RequiredArgsConstructor
public class HotClusterService {

    /** 缓存 TTL：与热榜刷新周期一致（10 分钟） */
    private static final long CACHE_TTL_MS = 600_000L;

    private final HotService hotService;
    private final HotSimilarityService hotSimilarityService;
    private final CommonHotRefiner commonHotRefiner;

    /** 聚类结果缓存（单键） */
    private final ConcurrentHashMap<String, CacheEntry<List<SimilarHotClusterDTO>>> clusterCache =
            new ConcurrentHashMap<>();

    /**
     * 获取共同热点列表：快照 → 聚类 → 严格过滤，带 10 分钟缓存。
     */
    public List<SimilarHotClusterDTO> getClusters() {
        String key = "similar-clusters";
        CacheEntry<List<SimilarHotClusterDTO>> entry = clusterCache.get(key);
        if (entry != null && !entry.isExpired()) {
            return entry.value;
        }

        Map<String, List<HotItemDTO>> snapshot = hotService.getAllHotSnapshot();
        List<SimilarHotClusterDTO> data = commonHotRefiner.refine(
                hotSimilarityService.cluster(snapshot), snapshot);

        clusterCache.put(key, new CacheEntry<>(data, CACHE_TTL_MS));
        return data;
    }

    /** 仅供测试：清空缓存 */
    void clearCache() {
        clusterCache.clear();
    }

    /** 仅供测试：当前缓存条目数 */
    int cachedEntryCount() {
        return clusterCache.size();
    }

    /** 带 TTL 的缓存条目（复用 WeatherServiceImpl 的惯用法） */
    private static final class CacheEntry<T> {
        final T value;
        final long expireAt;

        CacheEntry(T value, long ttlMs) {
            this.value = value;
            this.expireAt = System.currentTimeMillis() + ttlMs;
        }

        boolean isExpired() {
            return System.currentTimeMillis() > expireAt;
        }
    }
}
