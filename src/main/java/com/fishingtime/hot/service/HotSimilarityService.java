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
 * 跨平台共同热点聚类 V1.3。
 *
 * 与 V1.2 的主要区别：
 * 1. 使用 Jieba 做真正的中文“词级”分词，不再用字符 n-gram 充当关键词；
 * 2. 使用当前热榜语料自动计算 token IDF，不维护人工热点词表；
 * 3. 不再使用并查集，避免 A≈B、B≈C 就把 A/B/C 强行合并的 single-link chaining；
 * 4. 标题加入事件簇时，同时看“与簇中心的相似度”和“与簇内最佳标题的相似度”；
 * 5. 同平台标题可以属于同一事件，但最终仍要求至少两个不同平台才展示；
 * 6. 展示标题只从完整分词 token 中产生，避免“年国内手机”“准中国”这类断词结果；
 * 7. 排序优先看覆盖平台数，其次看各平台排名共识，不再用单个平台最高热力值主导排序。
 */
@Service
public class HotSimilarityService {

    private static final double NEW_CLUSTER_MATCH_THRESHOLD = 0.50D;
    private static final double EXISTING_CLUSTER_MATCH_THRESHOLD = 0.40D;
    private static final double WEAK_ATTACH_THRESHOLD = 0.32D;

    private static final Pattern CLEAN_TO_SPACE = Pattern.compile("[^\\p{IsHan}a-zA-Z0-9]+");
    private static final Pattern PURE_NUMBER = Pattern.compile("^\\d+$");

    /** 通用语气/功能停用词，不包含任何具体热点实体。 */
    private static final Set<String> STOP_WORDS = Set.of(
            "的", "了", "是", "在", "和", "与", "及", "或", "将", "把", "被", "对", "到", "从", "为", "有", "也",
            "又", "都", "就", "还", "已", "已是", "正在", "进行", "表示", "回应", "称", "发布", "最新", "目前", "今日",
            "如何", "怎么", "为什么", "什么", "哪些", "一个", "一名", "网友", "现场", "消息", "视频", "相关", "正式",
            "预计", "再次", "持续", "成为", "引发", "引热议", "来了", "去哪", "哪了", "能否", "可能", "开始"
    );

    private final JiebaSegmenter segmenter = new JiebaSegmenter();

    public List<SimilarHotClusterDTO> cluster(Map<String, List<HotItemDTO>> platformData) {
        List<Candidate> candidates = buildCandidates(platformData);
        if (candidates.size() < 2) return List.of();

        CorpusStats stats = new CorpusStats(candidates);

        // 高热度/高排名标题先成为事件核心，后续标题围绕已有“事件中心”归类。
        candidates.sort(Comparator
                .comparingInt((Candidate c) -> safeScore(c.item)).reversed()
                .thenComparingInt(c -> safeRank(c.item)));

        List<EventCluster> events = new ArrayList<>();
        for (Candidate candidate : candidates) {
            Match best = findBestCluster(candidate, events, stats);
            if (best != null && best.accepted) {
                best.cluster.add(candidate);
            } else {
                events.add(new EventCluster(candidate));
            }
        }

        // 第二遍只处理孤立标题：允许它以“高 IDF 稀有共享词”弱加入一个已经稳定的事件簇。
        // 解决“中央气象台升级发布台风红色预警”这类没有直接写出“白海豚”的标题。
        attachWeakSingletons(events, stats);

        List<EventView> views = new ArrayList<>();
        for (EventCluster event : events) {
            Set<String> platforms = event.members.stream().map(c -> c.platform).collect(Collectors.toSet());
            if (platforms.size() < 2) continue;

            Map<String, CandidateScore> bestPerPlatform = chooseBestPerPlatform(event, stats);
            if (bestPerPlatform.size() < 2) continue;

            List<PlatformHotItemDTO> items = bestPerPlatform.values().stream()
                    .sorted(Comparator
                            .comparingInt((CandidateScore x) -> safeRank(x.candidate.item))
                            .thenComparingDouble((CandidateScore x) -> x.score).reversed())
                    .limit(3)
                    .map(x -> PlatformHotItemDTO.builder()
                            .platform(x.candidate.platform)
                            .hotItem(x.candidate.item)
                            .similarityScore(round(x.score))
                            .build())
                    .collect(Collectors.toList());

            double rankConsensus = items.stream()
                    .map(PlatformHotItemDTO::getHotItem)
                    .mapToDouble(item -> rankWeight(safeRank(item)))
                    .sum();

            views.add(new EventView(
                    SimilarHotClusterDTO.builder()
                            .title(buildClusterTitle(event, stats))
                            .sourceCount(platforms.size())
                            .items(items)
                            .build(),
                    rankConsensus,
                    event.members.size()
            ));
        }

        views.sort(Comparator
                .comparingInt((EventView x) -> x.dto.getSourceCount()).reversed()
                .thenComparingDouble((EventView x) -> x.rankConsensus).reversed()
                .thenComparingInt((EventView x) -> x.memberCount).reversed());

        return views.stream().map(x -> x.dto).collect(Collectors.toList());
    }

    private List<Candidate> buildCandidates(Map<String, List<HotItemDTO>> platformData) {
        List<Candidate> result = new ArrayList<>();
        platformData.forEach((platform, items) -> {
            if (items == null) return;
            for (HotItemDTO item : items) {
                if (item == null || item.getTitle() == null || item.getTitle().trim().isEmpty()) continue;
                String normalized = normalize(item.getTitle());
                List<String> tokens = tokenize(normalized);
                if (tokens.isEmpty()) continue;
                result.add(new Candidate(platform, item, normalized, tokens));
            }
        });
        return result;
    }

    private List<String> tokenize(String text) {
        List<String> raw = segmenter.sentenceProcess(text);
        List<String> result = new ArrayList<>();
        for (String token : raw) {
            if (token == null) continue;
            String t = token.trim().toLowerCase(Locale.ROOT);
            if (t.isEmpty() || STOP_WORDS.contains(t) || PURE_NUMBER.matcher(t).matches()) continue;
            if (t.length() == 1 && containsHan(t)) continue;
            result.add(t);
        }
        return result;
    }

    private Match findBestCluster(Candidate candidate, List<EventCluster> clusters, CorpusStats stats) {
        EventCluster bestCluster = null;
        double bestScore = 0D;
        double bestPair = 0D;
        double bestCenter = 0D;

        for (EventCluster cluster : clusters) {
            double pair = maxPairSimilarity(candidate, cluster, stats);
            double center = centerSimilarity(candidate, cluster, stats);
            double score = 0.55D * center + 0.45D * pair;

            if (score > bestScore) {
                bestScore = score;
                bestCluster = cluster;
                bestPair = pair;
                bestCenter = center;
            }
        }

        if (bestCluster == null) return null;

        double threshold = bestCluster.members.size() == 1
                ? NEW_CLUSTER_MATCH_THRESHOLD
                : EXISTING_CLUSTER_MATCH_THRESHOLD;

        boolean hasCoreOverlap = hasUsefulCoreOverlap(candidate, bestCluster, stats);
        boolean accepted = (bestScore >= threshold && hasCoreOverlap)
                || bestCenter >= 0.62D
                || bestPair >= 0.72D;

        return new Match(bestCluster, bestScore, accepted);
    }

    private void attachWeakSingletons(List<EventCluster> events, CorpusStats stats) {
        List<EventCluster> stable = events.stream()
                .filter(e -> e.members.size() >= 2)
                .collect(Collectors.toList());
        if (stable.isEmpty()) return;

        List<EventCluster> singletons = events.stream()
                .filter(e -> e.members.size() == 1)
                .collect(Collectors.toList());

        Set<EventCluster> remove = new HashSet<>();
        for (EventCluster singleton : singletons) {
            Candidate candidate = singleton.members.get(0);
            EventCluster best = null;
            double bestScore = 0D;

            for (EventCluster target : stable) {
                double pair = maxPairSimilarity(candidate, target, stats);
                double center = centerSimilarity(candidate, target, stats);
                double rareBridge = rareSharedTokenBridge(candidate, target, stats);
                double score = Math.max(0.55D * center + 0.45D * pair, rareBridge);
                if (score > bestScore) {
                    bestScore = score;
                    best = target;
                }
            }

            if (best != null && bestScore >= WEAK_ATTACH_THRESHOLD) {
                best.add(candidate);
                remove.add(singleton);
            }
        }
        events.removeAll(remove);
    }

    /**
     * 稀有词桥接：共享词越少见越像事件实体；像“中国”“发布”这类全榜高频词 IDF 较低，不会轻易桥接。
     */
    private double rareSharedTokenBridge(Candidate candidate, EventCluster cluster, CorpusStats stats) {
        Set<String> clusterTokens = cluster.allTokens();
        double best = 0D;
        for (String token : candidate.tokenSet) {
            if (!clusterTokens.contains(token)) continue;
            double idf = stats.idf(token);
            if (idf < 2.0D) continue;
            double score = Math.min(0.46D, 0.22D + 0.07D * idf + Math.min(token.length(), 4) * 0.015D);
            best = Math.max(best, score);
        }
        return best;
    }

    private boolean hasUsefulCoreOverlap(Candidate candidate, EventCluster cluster, CorpusStats stats) {
        Set<String> center = cluster.centerTokens();
        for (String token : candidate.tokenSet) {
            if (center.contains(token) && stats.idf(token) >= 1.35D) return true;
        }
        return false;
    }

    private double maxPairSimilarity(Candidate candidate, EventCluster cluster, CorpusStats stats) {
        double max = 0D;
        for (Candidate member : cluster.members) {
            max = Math.max(max, tokenSimilarity(candidate.tokenSet, member.tokenSet, stats));
        }
        return max;
    }

    private double centerSimilarity(Candidate candidate, EventCluster cluster, CorpusStats stats) {
        return tokenSimilarity(candidate.tokenSet, cluster.centerTokens(), stats);
    }

    private double tokenSimilarity(Set<String> a, Set<String> b, CorpusStats stats) {
        if (a.isEmpty() || b.isEmpty()) return 0D;

        Set<String> intersection = new HashSet<>(a);
        intersection.retainAll(b);
        if (intersection.isEmpty()) return 0D;

        Set<String> union = new HashSet<>(a);
        union.addAll(b);

        double intersectionWeight = intersection.stream().mapToDouble(stats::tokenWeight).sum();
        double unionWeight = union.stream().mapToDouble(stats::tokenWeight).sum();
        double aWeight = a.stream().mapToDouble(stats::tokenWeight).sum();
        double bWeight = b.stream().mapToDouble(stats::tokenWeight).sum();

        double weightedJaccard = unionWeight == 0D ? 0D : intersectionWeight / unionWeight;
        double containment = Math.min(aWeight, bWeight) == 0D ? 0D : intersectionWeight / Math.min(aWeight, bWeight);

        return 0.68D * weightedJaccard + 0.32D * containment;
    }

    private Map<String, CandidateScore> chooseBestPerPlatform(EventCluster event, CorpusStats stats) {
        Map<String, CandidateScore> result = new HashMap<>();
        for (Candidate candidate : event.members) {
            double score = centerSimilarity(candidate, event, stats);
            CandidateScore old = result.get(candidate.platform);
            if (old == null
                    || score > old.score
                    || (Math.abs(score - old.score) < 0.0001D
                    && safeRank(candidate.item) < safeRank(old.candidate.item))) {
                result.put(candidate.platform, new CandidateScore(candidate, score));
            }
        }
        return result;
    }

    /**
     * 共同热点标题只由完整 token 组成；优先选择“多标题、多平台都支持 + IDF 较高”的词。
     */
    private String buildClusterTitle(EventCluster event, CorpusStats stats) {
        Map<String, Integer> docSupport = new HashMap<>();
        Map<String, Set<String>> platformSupport = new HashMap<>();

        for (Candidate member : event.members) {
            for (String token : member.tokenSet) {
                docSupport.merge(token, 1, Integer::sum);
                platformSupport.computeIfAbsent(token, k -> new HashSet<>()).add(member.platform);
            }
        }

        List<TokenScore> scored = docSupport.entrySet().stream()
                .filter(e -> e.getValue() >= 2)
                .filter(e -> stats.idf(e.getKey()) >= 1.20D)
                .map(e -> {
                    String token = e.getKey();
                    int docs = e.getValue();
                    int platforms = platformSupport.getOrDefault(token, Set.of()).size();
                    double score = docs * stats.idf(token) * (1D + 0.35D * platforms) * Math.min(token.length(), 4);
                    return new TokenScore(token, score, docs, platforms);
                })
                .sorted(Comparator.comparingDouble((TokenScore x) -> x.score).reversed())
                .collect(Collectors.toList());

        if (scored.isEmpty()) return chooseAnchor(event, stats).item.getTitle();

        // 一个 3+ 字且得到多个标题支持的实体本身就可作为标题，如“白海豚”。
        TokenScore first = scored.get(0);
        if (scored.size() == 1 && first.token.length() >= 3) return first.token;

        Set<String> selected = scored.stream()
                .limit(5)
                .map(x -> x.token)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Candidate anchor = chooseAnchor(event, stats);
        List<String> ordered = anchor.tokens.stream()
                .filter(selected::contains)
                .distinct()
                .collect(Collectors.toList());

        // anchor 不一定包含所有高分词，把缺失词补到尾部。
        for (String token : selected) {
            if (!ordered.contains(token)) ordered.add(token);
        }

        StringBuilder title = new StringBuilder();
        int used = 0;
        for (String token : ordered) {
            if (used >= 4) break;
            if (title.length() + token.length() > 12) continue;
            title.append(token);
            used++;
        }

        // 只有一个 2 字普通词时信息不足，宁可回退代表标题，也不生成“准中国”式残缺标题。
        if (used == 1 && title.length() <= 2) return anchor.item.getTitle();
        return title.length() == 0 ? anchor.item.getTitle() : title.toString();
    }

    private Candidate chooseAnchor(EventCluster event, CorpusStats stats) {
        Candidate best = event.members.get(0);
        double bestScore = -1D;
        for (Candidate candidate : event.members) {
            double affinity = centerSimilarity(candidate, event, stats);
            double rankBonus = rankWeight(safeRank(candidate.item)) * 0.15D;
            double score = affinity + rankBonus;
            if (score > bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        return best;
    }

    private static String normalize(String title) {
        String value = title.toLowerCase(Locale.ROOT)
                .replace('＃', '#')
                .replace("#", " ")
                .replace("【", " ")
                .replace("】", " ");
        return CLEAN_TO_SPACE.matcher(value).replaceAll(" ").trim().replaceAll("\\s+", " ");
    }

    private static boolean containsHan(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.UnicodeScript.of(value.charAt(i)) == Character.UnicodeScript.HAN) return true;
        }
        return false;
    }

    private static double rankWeight(int rank) {
        if (rank <= 0 || rank == Integer.MAX_VALUE) return 0D;
        return 1D / (Math.log(rank + 1D) / Math.log(2D));
    }

    private static int safeRank(HotItemDTO item) {
        return item.getRank() == null ? Integer.MAX_VALUE : item.getRank();
    }

    private static int safeScore(HotItemDTO item) {
        return item.getNormalizedHotScore() == null ? 0 : item.getNormalizedHotScore();
    }

    private static double round(double value) {
        return Math.round(value * 1000D) / 1000D;
    }

    private static final class Candidate {
        final String platform;
        final HotItemDTO item;
        final String normalizedTitle;
        final List<String> tokens;
        final Set<String> tokenSet;

        Candidate(String platform, HotItemDTO item, String normalizedTitle, List<String> tokens) {
            this.platform = platform;
            this.item = item;
            this.normalizedTitle = normalizedTitle;
            this.tokens = List.copyOf(tokens);
            this.tokenSet = new LinkedHashSet<>(tokens);
        }
    }

    private static final class EventCluster {
        final List<Candidate> members = new ArrayList<>();

        EventCluster(Candidate seed) {
            members.add(seed);
        }

        void add(Candidate candidate) {
            members.add(candidate);
        }

        Set<String> allTokens() {
            Set<String> result = new HashSet<>();
            for (Candidate c : members) result.addAll(c.tokenSet);
            return result;
        }

        Set<String> centerTokens() {
            Map<String, Integer> support = new HashMap<>();
            for (Candidate c : members) {
                for (String token : c.tokenSet) support.merge(token, 1, Integer::sum);
            }
            int required = members.size() <= 2 ? 1 : Math.max(2, (int) Math.ceil(members.size() * 0.40D));
            return support.entrySet().stream()
                    .filter(e -> e.getValue() >= required)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toSet());
        }
    }

    private static final class CorpusStats {
        final int documentCount;
        final Map<String, Integer> df = new HashMap<>();

        CorpusStats(List<Candidate> candidates) {
            this.documentCount = candidates.size();
            for (Candidate candidate : candidates) {
                for (String token : candidate.tokenSet) df.merge(token, 1, Integer::sum);
            }
        }

        double idf(String token) {
            int frequency = df.getOrDefault(token, 0);
            return Math.log((documentCount + 1D) / (frequency + 1D)) + 1D;
        }

        double tokenWeight(String token) {
            return idf(token) * Math.min(Math.max(token.length(), 1), 4);
        }
    }

    private static final class Match {
        final EventCluster cluster;
        final double score;
        final boolean accepted;

        Match(EventCluster cluster, double score, boolean accepted) {
            this.cluster = cluster;
            this.score = score;
            this.accepted = accepted;
        }
    }

    private static final class CandidateScore {
        final Candidate candidate;
        final double score;

        CandidateScore(Candidate candidate, double score) {
            this.candidate = candidate;
            this.score = score;
        }
    }

    private static final class TokenScore {
        final String token;
        final double score;
        final int docSupport;
        final int platformSupport;

        TokenScore(String token, double score, int docSupport, int platformSupport) {
            this.token = token;
            this.score = score;
            this.docSupport = docSupport;
            this.platformSupport = platformSupport;
        }
    }

    private static final class EventView {
        final SimilarHotClusterDTO dto;
        final double rankConsensus;
        final int memberCount;

        EventView(SimilarHotClusterDTO dto, double rankConsensus, int memberCount) {
            this.dto = dto;
            this.rankConsensus = rankConsensus;
            this.memberCount = memberCount;
        }
    }
}
