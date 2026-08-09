package com.fishingtime.hot.service;

import com.fishingtime.hot.dto.HotItemDTO;
import com.fishingtime.hot.dto.PlatformHotItemDTO;
import com.fishingtime.hot.dto.SimilarHotClusterDTO;
import com.huaban.analysis.jieba.JiebaSegmenter;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 共同热点 V1.4 严格过滤层。
 *
 * 目标：
 * 1. 一个共同热点至少存在 2 个“跨平台共同关键词”；
 * 2. 某个平台要计入该共同热点，本平台代表标题也必须命中至少 2 个共同关键词；
 * 3. 展示标题只展示关键词，并使用空格分隔，不再拼成一句话；
 * 4. 用当前全量热榜语料的 IDF 降低“中国 / 美国 / 手机”等全局高频泛词的权重。
 */
@Service
public class CommonHotRefiner {

    private static final int MIN_CLUSTER_KEYWORDS = 2;
    private static final int MAX_DISPLAY_KEYWORDS = 4;
    private static final double MIN_KEYWORD_IDF = 1.25D;

    private static final Pattern CLEAN_TO_SPACE = Pattern.compile("[^\\p{IsHan}a-zA-Z0-9]+");
    private static final Pattern PURE_NUMBER = Pattern.compile("^\\d+$");

    /** 这里只放通用语气/功能词，不维护具体热点实体词。 */
    private static final Set<String> STOP_WORDS = Set.of(
            "的", "了", "是", "在", "和", "与", "及", "或", "将", "把", "被", "对", "到", "从", "为", "有", "也",
            "又", "都", "就", "还", "已", "正在", "进行", "表示", "回应", "称", "发布", "最新", "目前", "今日",
            "如何", "怎么", "为什么", "什么", "哪些", "一个", "一名", "网友", "现场", "消息", "视频", "相关", "正式",
            "预计", "再次", "持续", "成为", "引发", "引热议", "来了", "去哪", "哪了", "能否", "可能", "开始", "这个",
            "评价", "认为", "关于", "发生", "出现", "情况", "方面"
    );

    private final JiebaSegmenter segmenter = new JiebaSegmenter();

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
            Set<String> tokens = new LinkedHashSet<>(tokenize(entry.getHotItem().getTitle()));
            if (tokens.isEmpty()) continue;

            itemTokens.put(entry, tokens);
            for (String token : tokens) {
                tokenPlatforms.computeIfAbsent(token, k -> new HashSet<>()).add(entry.getPlatform());
                tokenDocs.merge(token, 1, Integer::sum);
            }
        }

        List<KeywordScore> keywords = tokenPlatforms.entrySet().stream()
                .filter(e -> e.getValue().size() >= 2)
                .filter(e -> stats.idf(e.getKey()) >= MIN_KEYWORD_IDF)
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

        if (keywords.size() < MIN_CLUSTER_KEYWORDS) return null;

        Set<String> clusterKeywords = keywords.stream()
                .map(x -> x.token)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        List<PlatformHotItemDTO> qualifiedItems = itemTokens.entrySet().stream()
                .filter(e -> overlapCount(e.getValue(), clusterKeywords) >= MIN_CLUSTER_KEYWORDS)
                .map(Map.Entry::getKey)
                .sorted(Comparator.comparingInt(x -> safeRank(x.getHotItem())))
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
                    return support >= 2;
                })
                .limit(MAX_DISPLAY_KEYWORDS)
                .map(k -> k.token)
                .collect(Collectors.toList());

        if (displayKeywords.size() < MIN_CLUSTER_KEYWORDS) return null;

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
                    Set<String> tokens = new HashSet<>(tokenize(item.getTitle()));
                    if (tokens.isEmpty()) continue;
                    docs++;
                    for (String token : tokens) df.merge(token, 1, Integer::sum);
                }
            }
        }

        return new CorpusStats(Math.max(docs, 1), df);
    }

    private List<String> tokenize(String title) {
        String normalized = CLEAN_TO_SPACE.matcher(title.toLowerCase(Locale.ROOT)).replaceAll(" ").trim();
        List<String> result = new ArrayList<>();
        for (String token : segmenter.sentenceProcess(normalized)) {
            if (token == null) continue;
            String value = token.trim().toLowerCase(Locale.ROOT);
            if (value.isEmpty() || STOP_WORDS.contains(value) || PURE_NUMBER.matcher(value).matches()) continue;
            if (value.length() == 1 && containsHan(value)) continue;
            result.add(value);
        }
        return result;
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
                .mapToDouble(item -> rankWeight(safeRank(item)))
                .sum();
    }

    private static double rankWeight(int rank) {
        if (rank <= 0 || rank == Integer.MAX_VALUE) return 0D;
        return 1D / (Math.log(rank + 1D) / Math.log(2D));
    }

    private static int safeRank(HotItemDTO item) {
        return item == null || item.getRank() == null ? Integer.MAX_VALUE : item.getRank();
    }

    private static boolean containsHan(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.UnicodeScript.of(value.charAt(i)) == Character.UnicodeScript.HAN) return true;
        }
        return false;
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
            return Math.log((documentCount + 1D) / (frequency + 1D)) + 1D;
        }
    }
}
