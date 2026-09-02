package com.fishingtime.hot.service;

import com.fishingtime.hot.dto.HotItemDTO;
import com.fishingtime.hot.dto.PlatformHotItemDTO;
import com.fishingtime.hot.dto.SimilarHotClusterDTO;
import com.fishingtime.hot.util.HotTextUtil;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * V2 共同热点识别：事件指纹聚类。
 *
 * 与旧版“标题 token 相似度”不同，本实现把标题拆成：
 * 1) 普通语义词；2) 数字实体；3) 动作/关系语义；4) 稀有核心词；5) 字符级相似度。
 * 先做候选事件匹配，再用“锚点 + 最佳成员”保守聚类，避免 single-link 串簇。
 *
 * 该类完全独立输出 SimilarHotClusterDTO，便于与旧 HotSimilarityService + CommonHotRefiner
 * 随时切换对比。
 */
@Service
public class EventFingerprintClusterService {

    private static final Pattern NUMBER = Pattern.compile("(?<!\\d)(\\d{2,6})(?!\\d)");

    /** 只做小规模、高确定性的语义归一，不维护大词典。 */
    private static final Map<String, String> SYNONYMS = buildSynonyms();

    private static final Set<String> ACTION_WORDS = Set.of(
            "退学", "辞职", "离职", "回应", "发声", "道歉", "去世", "离世", "结婚", "离婚",
            "起火", "爆炸", "坠毁", "失联", "获救", "被捕", "逮捕", "通报", "辟谣", "官宣",
            "夺冠", "晋级", "淘汰", "涨价", "降价", "下架", "召回", "开除", "停职", "辞退"
    );

    /** 过于泛化的词不作为“语义共同点”参与强匹配。 */
    private static final Set<String> GENERIC_WORDS = Set.of(
            "学生", "男子", "女子", "网友", "事件", "事情", "排名", "学校", "公司", "中国", "官方",
            "最新", "消息", "现场", "视频", "回应", "通报", "发布", "今日", "目前"
    );

    public List<SimilarHotClusterDTO> cluster(Map<String, List<HotItemDTO>> platformData) {
        List<Candidate> candidates = buildCandidates(platformData);
        if (candidates.size() < 2) return List.of();

        // 高排名优先作为事件锚点，降低后续聚类中心漂移。
        candidates.sort(Comparator
                .comparingInt((Candidate c) -> safeRank(c.item))
                .thenComparingInt((Candidate c) -> safeScore(c.item)).reversed());

        List<EventCluster> clusters = new ArrayList<>();
        for (Candidate candidate : candidates) {
            ClusterMatch best = findBestCluster(candidate, clusters);
            if (best != null && best.accepted) best.cluster.add(candidate);
            else clusters.add(new EventCluster(candidate));
        }

        List<EventView> result = new ArrayList<>();
        for (EventCluster cluster : clusters) {
            Set<String> platforms = cluster.members.stream().map(x -> x.platform).collect(Collectors.toSet());
            if (platforms.size() < 2) continue;

            Map<String, ScoredCandidate> bestPerPlatform = new HashMap<>();
            for (Candidate member : cluster.members) {
                double score = eventScore(member, cluster.anchor).score;
                ScoredCandidate old = bestPerPlatform.get(member.platform);
                if (old == null || score > old.score
                        || (Math.abs(score - old.score) < 0.0001 && safeRank(member.item) < safeRank(old.candidate.item))) {
                    bestPerPlatform.put(member.platform, new ScoredCandidate(member, score));
                }
            }

            if (bestPerPlatform.size() < 2) continue;

            List<PlatformHotItemDTO> items = bestPerPlatform.values().stream()
                    .sorted(Comparator
                            .comparingInt((ScoredCandidate x) -> safeRank(x.candidate.item))
                            .thenComparingDouble((ScoredCandidate x) -> x.score).reversed())
                    .limit(3)
                    .map(x -> PlatformHotItemDTO.builder()
                            .platform(x.candidate.platform)
                            .hotItem(x.candidate.item)
                            .similarityScore(round(x.score))
                            .build())
                    .collect(Collectors.toList());

            double rankConsensus = bestPerPlatform.values().stream()
                    .mapToDouble(x -> rankWeight(safeRank(x.candidate.item)))
                    .sum();
            double confidence = bestPerPlatform.values().stream().mapToDouble(x -> x.score).average().orElse(0D);

            result.add(new EventView(
                    SimilarHotClusterDTO.builder()
                            // 不再拼接关键词，直接用最有代表性的完整原标题，避免生成怪标题。
                            .title(cluster.anchor.item.getTitle())
                            .sourceCount(bestPerPlatform.size())
                            .items(items)
                            .build(),
                    rankConsensus,
                    confidence
            ));
        }

        result.sort(Comparator
                .comparingInt((EventView x) -> x.dto.getSourceCount()).reversed()
                .thenComparingDouble((EventView x) -> x.rankConsensus).reversed()
                .thenComparingDouble((EventView x) -> x.confidence).reversed());

        return result.stream().map(x -> x.dto).limit(10).collect(Collectors.toList());
    }

    private List<Candidate> buildCandidates(Map<String, List<HotItemDTO>> platformData) {
        List<Candidate> result = new ArrayList<>();
        platformData.forEach((platform, items) -> {
            if (items == null) return;
            for (HotItemDTO item : items) {
                if (item == null || item.getTitle() == null || item.getTitle().trim().isEmpty()) continue;
                String normalized = HotTextUtil.normalize(item.getTitle());
                List<String> rawTokens = HotTextUtil.segment(normalized);
                Set<String> semantic = rawTokens.stream()
                        .map(this::canonical)
                        .filter(x -> !x.isBlank())
                        .collect(Collectors.toCollection(LinkedHashSet::new));

                Set<String> numbers = extractNumbers(normalized);
                Set<String> actions = semantic.stream()
                        .filter(ACTION_WORDS::contains)
                        .collect(Collectors.toCollection(LinkedHashSet::new));
                Set<String> core = semantic.stream()
                        .filter(x -> !GENERIC_WORDS.contains(x))
                        .filter(x -> x.length() >= 2)
                        .collect(Collectors.toCollection(LinkedHashSet::new));

                if (semantic.isEmpty() && numbers.isEmpty()) continue;
                result.add(new Candidate(platform, item, normalized, semantic, numbers, actions, core));
            }
        });
        return result;
    }

    private ClusterMatch findBestCluster(Candidate candidate, List<EventCluster> clusters) {
        EventCluster bestCluster = null;
        MatchScore best = null;
        double bestComposite = -1D;

        for (EventCluster cluster : clusters) {
            MatchScore anchorScore = eventScore(candidate, cluster.anchor);
            MatchScore bestMember = anchorScore;
            for (Candidate member : cluster.members) {
                MatchScore s = eventScore(candidate, member);
                if (s.score > bestMember.score) bestMember = s;
            }

            // 保守聚类：最佳成员负责召回，锚点负责防止链式串簇。
            double composite = 0.62D * bestMember.score + 0.38D * anchorScore.score;
            boolean strong = bestMember.strongEvidence;
            boolean accepted = (composite >= 0.57D && anchorScore.score >= 0.38D)
                    || (strong && anchorScore.score >= 0.30D)
                    || anchorScore.strongEvidence;

            if (accepted && composite > bestComposite) {
                bestComposite = composite;
                bestCluster = cluster;
                best = new MatchScore(composite, strong || anchorScore.strongEvidence);
            }
        }
        return bestCluster == null ? null : new ClusterMatch(bestCluster, best, true);
    }

    /**
     * 两个标题是否属于同一事件。
     * 核心不是“句子像不像”，而是“事件指纹是否一致”。
     */
    private MatchScore eventScore(Candidate a, Candidate b) {
        int sharedNumbers = intersectionSize(a.numbers, b.numbers);
        int sharedCore = intersectionSize(a.core, b.core);
        int sharedSemantic = intersectionSize(a.semantic, b.semantic);
        int sharedActions = intersectionSize(a.actions, b.actions);

        double numberScore = jaccard(a.numbers, b.numbers);
        double coreScore = weightedContainment(a.core, b.core);
        double semanticScore = 0.55D * weightedContainment(a.semantic, b.semantic)
                + 0.45D * jaccard(a.semantic, b.semantic);
        double actionScore = jaccard(a.actions, b.actions);
        double charScore = diceBigrams(a.normalized, b.normalized);

        // 没有动作词时，不让 actionScore 贡献虚假的 1 分。
        double score = 0.34D * coreScore
                + 0.22D * numberScore
                + 0.14D * actionScore
                + 0.20D * semanticScore
                + 0.10D * charScore;

        boolean strongEvidence = false;

        // 两个相同数字 + 至少一个语义共同点，是非常强的事件指纹（260/2600 case）。
        if (sharedNumbers >= 2 && sharedSemantic >= 1) {
            score = Math.max(score, 0.86D);
            strongEvidence = true;
        }
        // 一个数字 + 两个核心语义 + 相同行为，也足够强。
        if (sharedNumbers >= 1 && sharedCore >= 2 && sharedActions >= 1) {
            score = Math.max(score, 0.80D);
            strongEvidence = true;
        }
        // 无数字新闻：至少两个核心实体/短语 + 同一动作。
        if (sharedCore >= 2 && sharedActions >= 1) {
            score = Math.max(score, 0.72D);
        }
        // 标题几乎改写但字符高度相似时兜底。
        if (charScore >= 0.72D && sharedSemantic >= 2) {
            score = Math.max(score, 0.70D);
        }

        // 只有一个普通词或一个数字绝不能强行聚类。
        if (sharedCore == 0 && sharedNumbers <= 1 && charScore < 0.55D) {
            score = Math.min(score, 0.36D);
        }

        return new MatchScore(Math.min(1D, score), strongEvidence);
    }

    private String canonical(String token) {
        String value = token == null ? "" : token.trim();
        return SYNONYMS.getOrDefault(value, value);
    }

    private Set<String> extractNumbers(String text) {
        Set<String> result = new LinkedHashSet<>();
        Matcher matcher = NUMBER.matcher(text);
        while (matcher.find()) {
            String n = matcher.group(1);
            // 两位数字噪声很大；只有紧邻“名/岁/元/万/强/号”等上下文时才保留。
            if (n.length() == 2) {
                int end = matcher.end();
                int start = matcher.start();
                String around = text.substring(Math.max(0, start - 2), Math.min(text.length(), end + 2));
                if (!(around.contains("名") || around.contains("岁") || around.contains("元")
                        || around.contains("万") || around.contains("强") || around.contains("号"))) continue;
            }
            result.add(n);
        }
        return result;
    }

    private static Map<String, String> buildSynonyms() {
        Map<String, String> m = new HashMap<>();
        // 关系
        m.put("舍友", "室友");
        m.put("同寝", "室友");
        m.put("同宿舍", "室友");
        // 工作
        m.put("离职", "辞职");
        m.put("辞去", "辞职");
        // 发声/回应
        m.put("发声", "回应");
        m.put("回应称", "回应");
        // 生死
        m.put("离世", "去世");
        m.put("逝世", "去世");
        // 执法
        m.put("逮捕", "被捕");
        m.put("抓获", "被捕");
        // 教育
        m.put("申请退学", "退学");
        return Collections.unmodifiableMap(m);
    }

    private static int intersectionSize(Set<String> a, Set<String> b) {
        if (a.isEmpty() || b.isEmpty()) return 0;
        int count = 0;
        Set<String> small = a.size() <= b.size() ? a : b;
        Set<String> large = a.size() <= b.size() ? b : a;
        for (String x : small) if (large.contains(x)) count++;
        return count;
    }

    private static double jaccard(Set<String> a, Set<String> b) {
        if (a.isEmpty() || b.isEmpty()) return 0D;
        int intersection = intersectionSize(a, b);
        int union = a.size() + b.size() - intersection;
        return union == 0 ? 0D : intersection / (double) union;
    }

    /** 对短标题比 Jaccard 更友好：共同核心词占较短标题的比例。 */
    private static double weightedContainment(Set<String> a, Set<String> b) {
        if (a.isEmpty() || b.isEmpty()) return 0D;
        double intersection = 0D;
        double wa = 0D;
        double wb = 0D;
        for (String x : a) wa += tokenWeight(x);
        for (String x : b) wb += tokenWeight(x);
        for (String x : a) if (b.contains(x)) intersection += tokenWeight(x);
        return Math.min(wa, wb) == 0D ? 0D : intersection / Math.min(wa, wb);
    }

    private static double tokenWeight(String token) {
        if (token == null) return 0D;
        return 1D + Math.min(token.length(), 5) * 0.18D;
    }

    private static double diceBigrams(String a, String b) {
        Set<String> x = bigrams(a);
        Set<String> y = bigrams(b);
        if (x.isEmpty() || y.isEmpty()) return 0D;
        int shared = intersectionSize(x, y);
        return (2D * shared) / (x.size() + y.size());
    }

    private static Set<String> bigrams(String text) {
        String value = text == null ? "" : text.replace(" ", "");
        Set<String> result = new HashSet<>();
        for (int i = 0; i + 1 < value.length(); i++) result.add(value.substring(i, i + 2));
        return result;
    }

    private static int safeRank(HotItemDTO item) {
        return item == null || item.getRank() == null ? Integer.MAX_VALUE : item.getRank();
    }

    private static int safeScore(HotItemDTO item) {
        return item == null || item.getNormalizedHotScore() == null ? 0 : item.getNormalizedHotScore();
    }

    private static double rankWeight(int rank) {
        if (rank <= 0 || rank == Integer.MAX_VALUE) return 0D;
        return 1D / (Math.log(rank + 1D) / Math.log(2D));
    }

    private static double round(double value) {
        return Math.round(value * 1000D) / 1000D;
    }

    private static final class Candidate {
        final String platform;
        final HotItemDTO item;
        final String normalized;
        final Set<String> semantic;
        final Set<String> numbers;
        final Set<String> actions;
        final Set<String> core;

        Candidate(String platform, HotItemDTO item, String normalized, Set<String> semantic,
                  Set<String> numbers, Set<String> actions, Set<String> core) {
            this.platform = platform;
            this.item = item;
            this.normalized = normalized;
            this.semantic = semantic;
            this.numbers = numbers;
            this.actions = actions;
            this.core = core;
        }
    }

    private static final class EventCluster {
        final Candidate anchor;
        final List<Candidate> members = new ArrayList<>();
        EventCluster(Candidate anchor) { this.anchor = anchor; this.members.add(anchor); }
        void add(Candidate c) { members.add(c); }
    }

    private static final class MatchScore {
        final double score;
        final boolean strongEvidence;
        MatchScore(double score, boolean strongEvidence) { this.score = score; this.strongEvidence = strongEvidence; }
    }

    private static final class ClusterMatch {
        final EventCluster cluster;
        final MatchScore score;
        final boolean accepted;
        ClusterMatch(EventCluster cluster, MatchScore score, boolean accepted) {
            this.cluster = cluster; this.score = score; this.accepted = accepted;
        }
    }

    private static final class ScoredCandidate {
        final Candidate candidate;
        final double score;
        ScoredCandidate(Candidate candidate, double score) { this.candidate = candidate; this.score = score; }
    }

    private static final class EventView {
        final SimilarHotClusterDTO dto;
        final double rankConsensus;
        final double confidence;
        EventView(SimilarHotClusterDTO dto, double rankConsensus, double confidence) {
            this.dto = dto; this.rankConsensus = rankConsensus; this.confidence = confidence;
        }
    }
}
