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
 * 共同热点 V1.5 精炼层。
 * 主通道保持 V1.4：至少 2 个跨平台共同关键词。
 * V1.5 仅在主通道失败时增加强实体兜底：一个高 IDF、非泛词实体若跨平台出现，
 * 且标题事件动作同义（如“离世/去世”）或除实体外仍有有效共同词，则允许召回。
 */
@Service
public class CommonHotRefiner {

    private static final Set<String> GENERIC_ENTITY_WORDS = Set.of(
            "中国", "美国", "女子", "男子", "网友", "手机", "警方", "孩子", "医院", "学校", "网红",
            "官方", "记者", "专家", "游客", "老人", "学生", "老师", "公司", "平台", "全国", "当地"
    );

    /** 只做稳定、低歧义的事件动作归一，不维护具体热点实体词。 */
    private static final Map<String, List<String>> EVENT_ACTION_GROUPS = new LinkedHashMap<>();
    static {
        EVENT_ACTION_GROUPS.put("DEATH", List.of("去世", "离世", "逝世", "病逝", "去逝", "身亡"));
        EVENT_ACTION_GROUPS.put("MARRIAGE", List.of("结婚", "领证", "官宣结婚", "登记结婚"));
        EVENT_ACTION_GROUPS.put("BREAKUP", List.of("分手", "宣布分手", "官宣分手"));
        EVENT_ACTION_GROUPS.put("ARREST", List.of("被捕", "落网", "抓获", "被抓", "拘捕"));
        EVENT_ACTION_GROUPS.put("PRICE_DOWN", List.of("降价", "降价了", "价格下调", "下调价格"));
        EVENT_ACTION_GROUPS.put("PRICE_UP", List.of("涨价", "提价", "价格上涨", "价格上调"));
        EVENT_ACTION_GROUPS.put("RESIGN", List.of("辞职", "辞任", "离职", "卸任"));
    }

    public List<SimilarHotClusterDTO> refine(List<SimilarHotClusterDTO> raw,
                                             Map<String, List<HotItemDTO>> allPlatformData) {
        if (raw == null || raw.isEmpty()) return List.of();
        CorpusStats stats = buildCorpusStats(allPlatformData);
        List<SimilarHotClusterDTO> result = new ArrayList<>();
        for (SimilarHotClusterDTO cluster : raw) {
            SimilarHotClusterDTO refined = refineOne(cluster, stats);
            if (refined != null) result.add(refined);
        }
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

        List<KeywordScore> keywords = buildKeywords(tokenPlatforms, tokenDocs, stats);
        if (keywords.size() >= HotClusterConstants.MIN_CLUSTER_KEYWORDS) {
            SimilarHotClusterDTO strict = refineStrict(itemTokens, tokenPlatforms, keywords);
            if (strict != null) return strict;
        }

        // V1.5 fallback：主通道失败后才执行，避免降低原有精度。
        return refineByStrongEntity(itemTokens, tokenPlatforms, stats);
    }

    private List<KeywordScore> buildKeywords(Map<String, Set<String>> tokenPlatforms,
                                             Map<String, Integer> tokenDocs,
                                             CorpusStats stats) {
        return tokenPlatforms.entrySet().stream()
                .filter(e -> e.getValue().size() >= HotClusterConstants.MIN_CLUSTER_KEYWORDS)
                .filter(e -> stats.idf(e.getKey()) >= HotClusterConstants.MIN_KEYWORD_IDF)
                .map(e -> {
                    String token = e.getKey();
                    int platformSupport = e.getValue().size();
                    int docSupport = tokenDocs.getOrDefault(token, 0);
                    double score = platformSupport * 2.0D + docSupport * 0.5D + stats.idf(token) * 1.5D
                            + Math.min(token.length(), 4) * 0.15D;
                    return new KeywordScore(token, score, platformSupport);
                })
                .sorted((a, b) -> {
                    int byPlatform = Integer.compare(b.platformSupport, a.platformSupport);
                    return byPlatform != 0 ? byPlatform : Double.compare(b.score, a.score);
                })
                .collect(Collectors.toList());
    }

    private SimilarHotClusterDTO refineStrict(Map<PlatformHotItemDTO, Set<String>> itemTokens,
                                               Map<String, Set<String>> tokenPlatforms,
                                               List<KeywordScore> keywords) {
        Set<String> clusterKeywords = keywords.stream().map(x -> x.token)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        List<PlatformHotItemDTO> qualifiedItems = itemTokens.entrySet().stream()
                .filter(e -> overlapCount(e.getValue(), clusterKeywords) >= HotClusterConstants.MIN_CLUSTER_KEYWORDS)
                .map(Map.Entry::getKey)
                .sorted(Comparator.comparingInt(x -> HotTextUtil.safeRank(x.getHotItem())))
                .limit(3).collect(Collectors.toList());
        long distinctPlatforms = distinctPlatformCount(qualifiedItems);
        if (distinctPlatforms < 2) return null;
        Set<String> survivingPlatforms = qualifiedItems.stream().map(PlatformHotItemDTO::getPlatform).collect(Collectors.toSet());
        List<String> displayKeywords = keywords.stream()
                .filter(k -> tokenPlatforms.getOrDefault(k.token, Set.of()).stream().filter(survivingPlatforms::contains).count()
                        >= HotClusterConstants.MIN_CLUSTER_KEYWORDS)
                .limit(HotClusterConstants.MAX_DISPLAY_KEYWORDS).map(k -> k.token).collect(Collectors.toList());
        if (displayKeywords.size() < HotClusterConstants.MIN_CLUSTER_KEYWORDS) return null;
        return SimilarHotClusterDTO.builder().title(String.join(" ", displayKeywords))
                .sourceCount((int) distinctPlatforms).items(qualifiedItems).build();
    }

    private SimilarHotClusterDTO refineByStrongEntity(Map<PlatformHotItemDTO, Set<String>> itemTokens,
                                                       Map<String, Set<String>> tokenPlatforms,
                                                       CorpusStats stats) {
        List<String> strongEntities = tokenPlatforms.entrySet().stream()
                .filter(e -> e.getValue().size() >= HotClusterConstants.STRONG_ENTITY_MIN_PLATFORMS)
                .map(Map.Entry::getKey)
                .filter(token -> isStrongEntity(token, stats))
                .sorted(Comparator.comparingDouble((String token) -> stats.idf(token)).reversed()
                        .thenComparingInt(String::length).reversed())
                .collect(Collectors.toList());

        for (String entity : strongEntities) {
            List<PlatformHotItemDTO> entityItems = itemTokens.entrySet().stream()
                    .filter(e -> e.getValue().contains(entity))
                    .map(Map.Entry::getKey)
                    .sorted(Comparator.comparingInt(x -> HotTextUtil.safeRank(x.getHotItem())))
                    .collect(Collectors.toList());
            if (distinctPlatformCount(entityItems) < 2) continue;

            List<PlatformHotItemDTO> qualified = new ArrayList<>();
            for (PlatformHotItemDTO item : entityItems) {
                if (qualified.isEmpty()) {
                    qualified.add(item);
                    continue;
                }
                boolean compatible = qualified.stream().anyMatch(other -> sameEvent(entity, item, other, itemTokens));
                if (compatible) qualified.add(item);
            }
            if (distinctPlatformCount(qualified) < 2) continue;

            List<PlatformHotItemDTO> top = qualified.stream()
                    .sorted(Comparator.comparingInt(x -> HotTextUtil.safeRank(x.getHotItem())))
                    .limit(3).collect(Collectors.toList());
            return SimilarHotClusterDTO.builder()
                    .title(entity)
                    .sourceCount((int) distinctPlatformCount(top))
                    .items(top)
                    .build();
        }
        return null;
    }

    private boolean isStrongEntity(String token, CorpusStats stats) {
        if (token == null || token.length() < HotClusterConstants.STRONG_ENTITY_MIN_LEN) return false;
        if (GENERIC_ENTITY_WORDS.contains(token)) return false;
        return stats.idf(token) >= HotClusterConstants.STRONG_ENTITY_MIN_IDF;
    }

    private boolean sameEvent(String entity, PlatformHotItemDTO a, PlatformHotItemDTO b,
                              Map<PlatformHotItemDTO, Set<String>> itemTokens) {
        String actionA = eventAction(a.getHotItem().getTitle());
        String actionB = eventAction(b.getHotItem().getTitle());
        if (actionA != null && actionA.equals(actionB)) return true;

        Set<String> residualA = new HashSet<>(itemTokens.getOrDefault(a, Set.of()));
        Set<String> residualB = new HashSet<>(itemTokens.getOrDefault(b, Set.of()));
        residualA.remove(entity);
        residualB.remove(entity);
        residualA.retainAll(residualB);
        residualA.removeAll(GENERIC_ENTITY_WORDS);
        return residualA.size() >= HotClusterConstants.STRONG_ENTITY_RESIDUAL_OVERLAP;
    }

    private String eventAction(String title) {
        String normalized = HotTextUtil.normalize(title);
        for (Map.Entry<String, List<String>> group : EVENT_ACTION_GROUPS.entrySet()) {
            for (String phrase : group.getValue()) {
                if (normalized.contains(phrase)) return group.getKey();
            }
        }
        return null;
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

    private static long distinctPlatformCount(List<PlatformHotItemDTO> items) {
        return items.stream().map(PlatformHotItemDTO::getPlatform).filter(Objects::nonNull).distinct().count();
    }

    private static int overlapCount(Set<String> a, Set<String> b) {
        int count = 0;
        for (String token : a) if (b.contains(token)) count++;
        return count;
    }

    private static double rankConsensus(SimilarHotClusterDTO cluster) {
        if (cluster.getItems() == null) return 0D;
        return cluster.getItems().stream().map(PlatformHotItemDTO::getHotItem).filter(Objects::nonNull)
                .mapToDouble(item -> HotTextUtil.rankWeight(HotTextUtil.safeRank(item))).sum();
    }

    private static final class KeywordScore {
        final String token; final double score; final int platformSupport;
        KeywordScore(String token, double score, int platformSupport) {
            this.token = token; this.score = score; this.platformSupport = platformSupport;
        }
    }

    private static final class CorpusStats {
        final int documentCount; final Map<String, Integer> df;
        CorpusStats(int documentCount, Map<String, Integer> df) { this.documentCount = documentCount; this.df = df; }
        double idf(String token) { return HotTextUtil.idf(documentCount, df.getOrDefault(token, 0)); }
    }
}
