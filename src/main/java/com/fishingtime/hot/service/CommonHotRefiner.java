package com.fishingtime.hot.service;

import com.fishingtime.hot.dto.HotItemDTO;
import com.fishingtime.hot.dto.PlatformHotItemDTO;
import com.fishingtime.hot.dto.SimilarHotClusterDTO;
import com.fishingtime.hot.util.HotClusterConstants;
import com.fishingtime.hot.util.HotTextUtil;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 共同热点 V1.4 严格过滤层。
 *
 * 目标：
 * 1. 一个共同热点至少存在 2 个“跨平台共同关键词”；
 * 2. 某个平台要计入该共同热点，本平台代表标题也必须命中至少 2 个共同关键词；
 * 3. 展示标题只展示关键词，并使用空格分隔，不再拼成一句话；
 * 4. 用当前全量热榜语料的 IDF 降低“中国 / 美国 / 手机”等全局高频泛词的权重。
 *
 * 分词/清洗/IDF 统一收敛到 {@link HotTextUtil}，决策阈值收敛到 {@link HotClusterConstants}。
 */
@Service
public class CommonHotRefiner {

    public List<SimilarHotClusterDTO> refine(List<SimilarHotClusterDTO> raw,
                                             Map<String, List<HotItemDTO>> allPlatformData) {
        if (raw == null || raw.isEmpty()) return List.of();

        CorpusStats stats = buildCorpusStats(allPlatformData);
        List<SimilarHotClusterDTO> result = new ArrayList<>();

        for (SimilarHotClusterDTO cluster : raw) {
            SimilarHotClusterDTO refined = refineOne(cluster, stats);
            if (refined != null) result.add(refined);
        }

        // 最终排序重新基于“严格过滤后”的真实平台覆盖与各平台排名共识，
        // 不继承上游宽松聚类的排序，避免错误簇挤掉真正的共同热点。
        result.sort((a, b) -> {
            int bySource = Integer.compare(b.getSourceCount(), a.getSourceCount());
            if (bySource != 0) return bySource;
            return Double.compare(rankConsensus(b), rankConsensus(a));
        });

        return result;
    }

    private SimilarHotClusterDTO refineOne(SimilarHotClusterDTO cluster, CorpusStats stats) {
        if (cluster == null || cluster.getItems() == null || cluster.getItems().size() < 2) return null;

        Map<PlatformHotItemDTO, Set<String>> itemTokens = new LinkedHashMap<>();
        Map<String, Set<String>> tokenPlatforms = new HashMap<>();
        Map<String, Integer> tokenDocs = new HashMap<>();

        for (PlatformHotItemDTO entry : cluster.getItems()) {
            if (entry == null || entry.getHotItem() == null || entry.getHotItem().getTitle() == null) continue;
            Set<String> tokens = new LinkedHashSet<>(HotTextUtil.tokenize(entry.getHotItem().getTitle()));
            if (tokens.isEmpty()) continue;

            itemTokens.put(entry, tokens);
            for (String token : tokens) {
                tokenPlatforms.computeIfAbsent(token, k -> new HashSet<>()).add(entry.getPlatform());
                tokenDocs.merge(token, 1, Integer::sum);
            }
        }

        List<KeywordScore> keywords = tokenPlatforms.entrySet().stream()
                .filter(e -> e.getValue().size() >= HotClusterConstants.MIN_CLUSTER_KEYWORDS)
                .filter(e -> stats.idf(e.getKey()) >= HotClusterConstants.MIN_KEYWORD_IDF)
                .map(e -> {
                    String token = e.getKey();
                    int platformSupport = e.getValue().size();
                    int docSupport = tokenDocs.getOrDefault(token, 0);
                    double score = platformSupport * 2.0D
                            + docSupport * 0.5D
                            + stats.idf(token) * 1.5D
                            + Math.min(token.length(), 4) * 0.15D;
                    return new KeywordScore(token, score, platformSupport);
                })
                .sorted((a, b) -> {
                    int byPlatform = Integer.compare(b.platformSupport, a.platformSupport);
                    if (byPlatform != 0) return byPlatform;
                    return Double.compare(b.score, a.score);
                })
                .collect(Collectors.toList());

        if (keywords.size() < HotClusterConstants.MIN_CLUSTER_KEYWORDS) return null;

        Set<String> clusterKeywords = keywords.stream()
                .map(x -> x.token)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        List<PlatformHotItemDTO> qualifiedItems = itemTokens.entrySet().stream()
                .filter(e -> overlapCount(e.getValue(), clusterKeywords) >= HotClusterConstants.MIN_CLUSTER_KEYWORDS)
                .map(Map.Entry::getKey)
                .sorted(Comparator.comparingInt(x -> HotTextUtil.safeRank(x.getHotItem())))
                .limit(3)
                .collect(Collectors.toList());

        long distinctPlatforms = qualifiedItems.stream()
                .map(PlatformHotItemDTO::getPlatform)
                .filter(Objects::nonNull)
                .distinct()
                .count();
        if (distinctPlatforms < 2) return null;

        Set<String> survivingPlatforms = qualifiedItems.stream()
                .map(PlatformHotItemDTO::getPlatform)
                .collect(Collectors.toSet());

        List<String> displayKeywords = keywords.stream()
                .filter(k -> {
                    Set<String> supported = tokenPlatforms.getOrDefault(k.token, Set.of());
                    long support = supported.stream().filter(survivingPlatforms::contains).count();
                    return support >= HotClusterConstants.MIN_CLUSTER_KEYWORDS;
                })
                .limit(HotClusterConstants.MAX_DISPLAY_KEYWORDS)
                .map(k -> k.token)
                .collect(Collectors.toList());

        if (displayKeywords.size() < HotClusterConstants.MIN_CLUSTER_KEYWORDS) return null;

        return SimilarHotClusterDTO.builder()
                .title(String.join(" ", displayKeywords))
                .sourceCount((int) distinctPlatforms)
                .items(qualifiedItems)
                .build();
    }

    private CorpusStats buildCorpusStats(Map<String, List<HotItemDTO>> allPlatformData) {
        Map<String, Integer> df = new HashMap<>();
        int docs = 0;

        if (allPlatformData != null) {
            for (List<HotItemDTO> items : allPlatformData.values()) {
                if (items == null) continue;
                for (HotItemDTO item : items) {
                    if (item == null || item.getTitle() == null || item.getTitle().trim().isEmpty()) continue;
                    Set<String> tokens = new HashSet<>(HotTextUtil.tokenize(item.getTitle()));
                    if (tokens.isEmpty()) continue;
                    docs++;
                    for (String token : tokens) df.merge(token, 1, Integer::sum);
                }
            }
        }

        return new CorpusStats(Math.max(docs, 1), df);
    }

    private static int overlapCount(Set<String> a, Set<String> b) {
        int count = 0;
        for (String token : a) if (b.contains(token)) count++;
        return count;
    }

    private static double rankConsensus(SimilarHotClusterDTO cluster) {
        if (cluster.getItems() == null) return 0D;
        return cluster.getItems().stream()
                .map(PlatformHotItemDTO::getHotItem)
                .filter(Objects::nonNull)
                .mapToDouble(item -> HotTextUtil.rankWeight(HotTextUtil.safeRank(item)))
                .sum();
    }

    private static final class KeywordScore {
        final String token;
        final double score;
        final int platformSupport;

        KeywordScore(String token, double score, int platformSupport) {
            this.token = token;
            this.score = score;
            this.platformSupport = platformSupport;
        }
    }

    private static final class CorpusStats {
        final int documentCount;
        final Map<String, Integer> df;

        CorpusStats(int documentCount, Map<String, Integer> df) {
            this.documentCount = documentCount;
            this.df = df;
        }

        double idf(String token) {
            int frequency = df.getOrDefault(token, 0);
            return HotTextUtil.idf(documentCount, frequency);
        }
    }
}
