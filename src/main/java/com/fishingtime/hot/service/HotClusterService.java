package com.fishingtime.hot.service;

import com.fishingtime.hot.dto.HotItemDTO;
import com.fishingtime.hot.dto.SimilarHotClusterDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 聚类门面服务：获取热榜快照并计算共同热点，结果缓存 10 分钟。
 *
 * 当前使用 EventFingerprintClusterService（V2 事件指纹算法）。
 * 旧版 HotSimilarityService + CommonHotRefiner 仍保留在代码中，
 * 如效果不理想，只需把 getClusters() 中的一行切回旧实现即可。
 */
@Service
@RequiredArgsConstructor
public class HotClusterService {

    /** 缓存 TTL：与热榜刷新周期一致（10 分钟） */
    private static final long CACHE_TTL_MS = 600_000L;

    private final HotService hotService;
    private final EventFingerprintClusterService eventFingerprintClusterService;

    /** 旧算法仍注入保留，方便随时切回 */
    private final HotSimilarityService hotSimilarityService;
    private final CommonHotRefiner commonHotRefiner;

    /** 聚类结果缓存（单键） */
    private final ConcurrentHashMap<String, CacheEntry<List<SimilarHotClusterDTO>>> clusterCache =
            new ConcurrentHashMap<>();

    /**
     * 获取共同热点列表：快照 → V2 事件指纹聚类，带 10 分钟缓存。
     */
    public List<SimilarHotClusterDTO> getClusters() {
        String key = "similar-clusters-v2";
        CacheEntry<List<SimilarHotClusterDTO>> entry = clusterCache.get(key);
        if (entry != null && !entry.isExpired()) {
            return entry.value;
        }

        Map<String, List<HotItemDTO>> snapshot = hotService.getAllHotSnapshot();

        // V2：事件级共同热点识别（当前启用）
        List<SimilarHotClusterDTO> data = eventFingerprintClusterService.cluster(snapshot);

        // 如需切回旧算法，替换上一行为：
        // List<SimilarHotClusterDTO> data = commonHotRefiner.refine(
        //         hotSimilarityService.cluster(snapshot), snapshot);

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

    /** 带 TTL 的缓存条目 */
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
