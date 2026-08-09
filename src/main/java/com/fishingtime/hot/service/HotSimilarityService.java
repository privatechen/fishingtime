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
 * 跨平台共同热点聚类 V1.4。
 *
 * 与 V1.3 相比：
 * 1. 分词/清洗/IDF/排名辅助统一收敛到 {@link HotTextUtil}，消除与 CommonHotRefiner 的重复实现；
 * 2. 决策阈值统一收敛到 {@link HotClusterConstants}；
 * 3. 新增簇间合并：增量聚类"先到先得"可能把同一事件拆成多个簇，聚类完成后逐对合并相近簇，提高召回。
 *
 * 保留的设计要点：
 * 1. Jieba 做词级分词，不再用字符 n-gram 充当关键词；
 * 2. 用当前热榜语料自动计算 token IDF，不维护人工热点词表；
 * 3. 不使用并查集，避免 A≈B、B≈C 把 A/B/C 强行合并的 single-link chaining；
 * 4. 标题加入事件簇时，同时看"与簇中心相似度"和"与簇内最佳标题相似度"；
 * 5. 同平台标题可属同一事件，但最终仍要求至少两个不同平台才展示；
 * 6. 展示标题只从完整分词 token 中产生，避免"年国内手机""准中国"这类断词结果；
 * 7. 排序优先看覆盖平台数，其次看各平台排名共识。
 */
@Service
public class HotSimilarityService {

    public List<SimilarHotClusterDTO> cluster(Map<String, List<HotItemDTO>> platformData) {
        List<Candidate> candidates = buildCandidates(platformData);
        if (candidates.size() < 2) return List.of();

        CorpusStats stats = new CorpusStats(candidates);

        // 高热度/高排名标题先成为事件核心，后续标题围绕已有"事件中心"归类。
        candidates.sort(Comparator
                .comparingInt((Candidate c) -> HotTextUtil.safeScore(c.item)).reversed()
                .thenComparingInt(c -> HotTextUtil.safeRank(c.item)));

        List<EventCluster> events = new ArrayList<>();
        for (Candidate candidate : candidates) {
            Match best = findBestCluster(candidate, events, stats);
            if (best != null && best.accepted) {
                best.cluster.add(candidate);
            } else {
                events.add(new EventCluster(candidate));
            }
        }

        // 第二遍只处理孤立标题：允许它以"高 IDF 稀有共享词"弱加入一个已经稳定的事件簇。
        // 解决"中央气象台升级发布台风红色预警"这类没有直接写出"白海豚"的标题。
        attachWeakSingletons(events, stats);

        // 簇间合并：修复增量聚类把同一事件拆成多个簇的召回缺口。
        if (HotClusterConstants.CLUSTER_MERGE_ENABLED) {
            mergeSimilarClusters(events, stats);
        }

        List<EventView> views = new ArrayList<>();
        for (EventCluster event : events) {
            Set<String> platforms = event.members.stream().map(c -> c.platform).collect(Collectors.toSet());
            if (platforms.size() < 2) continue;

            Map<String, CandidateScore> bestPerPlatform = chooseBestPerPlatform(event, stats);
            if (bestPerPlatform.size() < 2) continue;

            List<PlatformHotItemDTO> items = bestPerPlatform.values().stream()
                    .sorted(Comparator
                            .comparingInt((CandidateScore x) -> HotTextUtil.safeRank(x.candidate.item))
                            .thenComparingDouble((CandidateScore x) -> x.score).reversed())
                    .limit(3)
                    .map(x -> PlatformHotItemDTO.builder()
                            .platform(x.candidate.platform)
                            .hotItem(x.candidate.item)
                            .similarityScore(HotTextUtil.round(x.score))
                            .build())
                    .collect(Collectors.toList());

            double rankConsensus = items.stream()
                    .map(PlatformHotItemDTO::getHotItem)
                    .mapToDouble(item -> HotTextUtil.rankWeight(HotTextUtil.safeRank(item)))
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
                String normalized = HotTextUtil.normalize(item.getTitle());
                List<String> tokens = HotTextUtil.segment(normalized);
                if (tokens.isEmpty()) continue;
                result.add(new Candidate(platform, item, normalized, tokens));
            }
        });
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
            double score = HotClusterConstants.CENTER_WEIGHT * center
                    + HotClusterConstants.PAIR_WEIGHT * pair;

            if (score > bestScore) {
                bestScore = score;
                bestCluster = cluster;
                bestPair = pair;
                bestCenter = center;
            }
        }

        if (bestCluster == null) return null;

        double threshold = bestCluster.members.size() == 1
                ? HotClusterConstants.NEW_CLUSTER_MATCH_THRESHOLD
                : HotClusterConstants.EXISTING_CLUSTER_MATCH_THRESHOLD;

        boolean hasCoreOverlap = hasUsefulCoreOverlap(candidate, bestCluster, stats);
        boolean accepted = (bestScore >= threshold && hasCoreOverlap)
                || bestCenter >= HotClusterConstants.CENTER_ACCEPT_THRESHOLD
                || bestPair >= HotClusterConstants.PAIR_ACCEPT_THRESHOLD;

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
                double score = Math.max(HotClusterConstants.CENTER_WEIGHT * center
                        + HotClusterConstants.PAIR_WEIGHT * pair, rareBridge);
                if (score > bestScore) {
                    bestScore = score;
                    best = target;
                }
            }

            if (best != null && bestScore >= HotClusterConstants.WEAK_ATTACH_THRESHOLD) {
                best.add(candidate);
                remove.add(singleton);
            }
        }
        events.removeAll(remove);
    }

    /**
     * 稀有词桥接：共享词越少见越像事件实体；像"中国""发布"这类全榜高频词 IDF 较低，不会轻易桥接。
     */
    private double rareSharedTokenBridge(Candidate candidate, EventCluster cluster, CorpusStats stats) {
        Set<String> clusterTokens = cluster.allTokens();
        double best = 0D;
        for (String token : candidate.tokenSet) {
            if (!clusterTokens.contains(token)) continue;
            double idf = stats.idf(token);
            if (idf < HotClusterConstants.RARE_BRIDGE_MIN_IDF) continue;
            double score = Math.min(HotClusterConstants.RARE_BRIDGE_CAP,
                    HotClusterConstants.RARE_BRIDGE_BASE
                            + HotClusterConstants.RARE_BRIDGE_IDF_FACTOR * idf
                            + Math.min(token.length(), 4) * HotClusterConstants.RARE_BRIDGE_LEN_FACTOR);
            best = Math.max(best, score);
        }
        return best;
    }

    private boolean hasUsefulCoreOverlap(Candidate candidate, EventCluster cluster, CorpusStats stats) {
        Set<String> center = cluster.centerTokens();
        for (String token : candidate.tokenSet) {
            if (center.contains(token) && stats.idf(token) >= HotClusterConstants.CORE_OVERLAP_MIN_IDF) return true;
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

        return HotClusterConstants.TOKEN_WEIGHTED_JACCARD_WEIGHT * weightedJaccard
                + HotClusterConstants.TOKEN_CONTAINMENT_WEIGHT * containment;
    }

    private Map<String, CandidateScore> chooseBestPerPlatform(EventCluster event, CorpusStats stats) {
        Map<String, CandidateScore> result = new HashMap<>();
        for (Candidate candidate : event.members) {
            double score = centerSimilarity(candidate, event, stats);
            CandidateScore old = result.get(candidate.platform);
            if (old == null
                    || score > old.score
                    || (Math.abs(score - old.score) < 0.0001D
                    && HotTextUtil.safeRank(candidate.item) < HotTextUtil.safeRank(old.candidate.item))) {
                result.put(candidate.platform, new CandidateScore(candidate, score));
            }
        }
        return result;
    }

    /**
     * 共同热点标题只由完整 token 组成；优先选择"多标题、多平台都支持 + IDF 较高"的词。
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
                .filter(e -> stats.idf(e.getKey()) >= HotClusterConstants.TITLE_TOKEN_MIN_IDF)
                .map(e -> {
                    String token = e.getKey();
                    int docs = e.getValue();
                    int platforms = platformSupport.getOrDefault(token, Set.of()).size();
                    double score = docs * stats.idf(token)
                            * (1D + HotClusterConstants.TITLE_TOKEN_PLATFORM_FACTOR * platforms)
                            * Math.min(token.length(), 4);
                    return new TokenScore(token, score, docs, platforms);
                })
                .sorted(Comparator.comparingDouble((TokenScore x) -> x.score).reversed())
                .collect(Collectors.toList());

        if (scored.isEmpty()) return chooseAnchor(event, stats).item.getTitle();

        // 一个 3+ 字且得到多个标题支持的实体本身就可作为标题，如"白海豚"。
        TokenScore first = scored.get(0);
        if (scored.size() == 1 && first.token.length() >= HotClusterConstants.TITLE_SINGLE_WORD_MIN_LEN) {
            return first.token;
        }

        Set<String> selected = scored.stream()
                .limit(HotClusterConstants.TITLE_POOL_SIZE)
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
            if (used >= HotClusterConstants.TITLE_MAX_TOKENS) break;
            if (title.length() + token.length() > HotClusterConstants.TITLE_MAX_CHARS) continue;
            title.append(token);
            used++;
        }

        // 只有一个 2 字普通词时信息不足，宁可回退代表标题，也不生成"准中国"式残缺标题。
        if (used == 1 && title.length() <= 2) return anchor.item.getTitle();
        return title.length() == 0 ? anchor.item.getTitle() : title.toString();
    }

    private Candidate chooseAnchor(EventCluster event, CorpusStats stats) {
        Candidate best = event.members.get(0);
        double bestScore = -1D;
        for (Candidate candidate : event.members) {
            double affinity = centerSimilarity(candidate, event, stats);
            double rankBonus = HotTextUtil.rankWeight(HotTextUtil.safeRank(candidate.item))
                    * HotClusterConstants.TITLE_ANCHOR_RANK_BONUS;
            double score = affinity + rankBonus;
            if (score > bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        return best;
    }

    // ────────────── 簇间合并（V1.4） ──────────────

    /**
     * 逐对合并相近簇，合并后重扫直到收敛（上限 CLUSTER_MERGE_MAX_ITERATIONS 轮）。
     * 不用并查集传递闭包，避免 single-link chaining 把不相关事件连成一个大簇。
     */
    private void mergeSimilarClusters(List<EventCluster> events, CorpusStats stats) {
        boolean merged = true;
        for (int round = 0; merged && round < HotClusterConstants.CLUSTER_MERGE_MAX_ITERATIONS; round++) {
            merged = false;
            for (int i = 0; i < events.size(); i++) {
                for (int j = i + 1; j < events.size(); j++) {
                    EventCluster a = events.get(i);
                    EventCluster b = events.get(j);
                    double core = clusterCoreSimilarity(a, b, stats);
                    double bridge = rareCoreBridge(a, b, stats);
                    if (core >= HotClusterConstants.CLUSTER_MERGE_CORE_THRESHOLD
                            || bridge >= HotClusterConstants.CLUSTER_MERGE_BRIDGE_THRESHOLD) {
                        a.members.addAll(b.members);
                        events.remove(j);
                        j--;
                        merged = true;
                    }
                }
            }
        }
    }

    /** 两簇"核心词集合"的加权相似度（复用 tokenSimilarity） */
    private double clusterCoreSimilarity(EventCluster a, EventCluster b, CorpusStats stats) {
        return tokenSimilarity(a.centerTokens(), b.centerTokens(), stats);
    }

    /**
     * 稀有核心词桥接：两簇核心词交集里 IDF≥RARE_BRIDGE_MIN_IDF 的稀有实体词，
     * 用稀有桥公式（与 rareSharedTokenBridge 一致）取最大分。
     */
    private double rareCoreBridge(EventCluster a, EventCluster b, CorpusStats stats) {
        Set<String> intersection = new HashSet<>(a.centerTokens());
        intersection.retainAll(b.centerTokens());
        double best = 0D;
        for (String token : intersection) {
            double idf = stats.idf(token);
            if (idf < HotClusterConstants.RARE_BRIDGE_MIN_IDF) continue;
            double score = Math.min(HotClusterConstants.RARE_BRIDGE_CAP,
                    HotClusterConstants.RARE_BRIDGE_BASE
                            + HotClusterConstants.RARE_BRIDGE_IDF_FACTOR * idf
                            + Math.min(token.length(), 4) * HotClusterConstants.RARE_BRIDGE_LEN_FACTOR);
            best = Math.max(best, score);
        }
        return best;
    }

    // ────────────── 内部结构 ──────────────

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
            int required = members.size() <= HotClusterConstants.CENTER_SUPPORT_SMALL_CLUSTER_MEMBERS
                    ? HotClusterConstants.CENTER_SUPPORT_SINGLE_MEMBER
                    : Math.max(HotClusterConstants.CENTER_SUPPORT_MULTI_MIN,
                            (int) Math.ceil(members.size() * HotClusterConstants.CENTER_SUPPORT_RATIO));
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
            return HotTextUtil.idf(documentCount, frequency);
        }

        double tokenWeight(String token) {
            int frequency = df.getOrDefault(token, 0);
            return HotTextUtil.tokenWeight(documentCount, frequency, token);
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
