package com.fishingtime.hot.service;

import com.fishingtime.hot.dto.HotItemDTO;
import com.fishingtime.hot.dto.PlatformHotItemDTO;
import com.fishingtime.hot.dto.SimilarHotClusterDTO;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 跨平台共同热点聚类 V1.2。
 *
 * 核心思路：
 * 1. “是不是同一个事件”与“是不是全网共同热点”分开判断；
 * 2. 聚类阶段允许同平台标题互相匹配；
 * 3. 先用强匹配形成基础事件簇，再允许弱匹配标题加入已有事件簇；
 * 4. 最终只有至少 2 个不同平台命中的事件簇才对外展示；
 * 5. 每个平台只展示 1 条代表标题，最多展示 3 个平台；
 * 6. 展示标题自动从簇内标题提取共同关键词/关键词组，不维护人工热点词库。
 */
@Service
public class HotSimilarityService {

    private static final double STRONG_THRESHOLD = 0.50D;
    private static final double WEAK_THRESHOLD = 0.28D;
    private static final Pattern NOISE = Pattern.compile("[\\p{P}\\p{S}\\s]+");

    public List<SimilarHotClusterDTO> cluster(Map<String, List<HotItemDTO>> platformData) {
        List<Candidate> all = buildCandidates(platformData);
        if (all.size() < 2) return List.of();

        CorpusStats stats = new CorpusStats(all);
        UnionFind uf = new UnionFind(all.size());

        // 第一阶段：强匹配。这里允许同平台互相聚类，先识别“同一事件”。
        for (int i = 0; i < all.size(); i++) {
            for (int j = i + 1; j < all.size(); j++) {
                double score = similarity(all.get(i), all.get(j), stats);
                if (score >= STRONG_THRESHOLD) {
                    uf.union(i, j);
                }
            }
        }

        // 第二阶段：弱匹配只允许加入已经由至少 2 条强匹配标题构成的事件簇。
        // 典型场景：
        // “白海豚10级风圈” + “台风白海豚到哪了” 已形成事件簇，
        // “中央气象台发布台风红色预警”虽然只共享“台风”，仍可作为弱关联成员加入。
        attachWeakCandidates(all, uf, stats);

        Map<Integer, List<Candidate>> groups = buildGroups(all, uf);
        List<SimilarHotClusterDTO> result = new ArrayList<>();

        for (List<Candidate> group : groups.values()) {
            Set<String> platforms = group.stream().map(c -> c.platform).collect(Collectors.toSet());

            // 同平台内部可以组成事件簇，但没有跨平台共识时不进入“全网共同热点”。
            if (platforms.size() < 2) continue;

            Candidate anchor = chooseAnchor(group, stats);
            Map<String, CandidateScore> bestByPlatform = chooseBestPerPlatform(group, stats);

            List<PlatformHotItemDTO> items = bestByPlatform.values().stream()
                    .sorted(Comparator
                            .comparingDouble((CandidateScore x) -> x.score).reversed()
                            .thenComparing((CandidateScore x) -> safeScore(x.candidate.item), Comparator.reverseOrder())
                            .thenComparingInt(x -> safeRank(x.candidate.item)))
                    .limit(3)
                    .map(x -> PlatformHotItemDTO.builder()
                            .platform(x.candidate.platform)
                            .hotItem(x.candidate.item)
                            .similarityScore(round(x.score))
                            .build())
                    .collect(Collectors.toList());

            if (items.size() < 2) continue;

            result.add(SimilarHotClusterDTO.builder()
                    .title(buildClusterTitle(group, anchor, stats))
                    .sourceCount(platforms.size())
                    .items(items)
                    .build());
        }

        // “共同程度”优先：平台数越多越靠前；同平台数时再看最高热度与排名。
        result.sort(Comparator
                .comparingInt(SimilarHotClusterDTO::getSourceCount).reversed()
                .thenComparingInt(c -> c.getItems().stream()
                        .map(PlatformHotItemDTO::getHotItem)
                        .mapToInt(HotSimilarityService::safeScore).max().orElse(0)).reversed()
                .thenComparingInt(c -> c.getItems().stream()
                        .map(PlatformHotItemDTO::getHotItem)
                        .mapToInt(HotSimilarityService::safeRank).min().orElse(Integer.MAX_VALUE)));

        return result;
    }

    private static List<Candidate> buildCandidates(Map<String, List<HotItemDTO>> platformData) {
        List<Candidate> all = new ArrayList<>();
        platformData.forEach((platform, items) -> {
            if (items == null) return;
            for (HotItemDTO item : items) {
                if (item == null || item.getTitle() == null || item.getTitle().trim().isEmpty()) continue;
                String normalized = normalize(item.getTitle());
                if (normalized.isEmpty()) continue;
                all.add(new Candidate(platform, item, normalized));
            }
        });
        return all;
    }

    private static Map<Integer, List<Candidate>> buildGroups(List<Candidate> all, UnionFind uf) {
        Map<Integer, List<Candidate>> groups = new HashMap<>();
        for (int i = 0; i < all.size(); i++) {
            groups.computeIfAbsent(uf.find(i), k -> new ArrayList<>()).add(all.get(i));
        }
        return groups;
    }

    private static void attachWeakCandidates(List<Candidate> all, UnionFind uf, CorpusStats stats) {
        boolean changed;
        int rounds = 0;
        do {
            changed = false;
            rounds++;
            Map<Integer, List<Integer>> indexGroups = new HashMap<>();
            for (int i = 0; i < all.size(); i++) {
                indexGroups.computeIfAbsent(uf.find(i), k -> new ArrayList<>()).add(i);
            }

            List<Integer> strongRoots = indexGroups.entrySet().stream()
                    .filter(e -> e.getValue().size() >= 2)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList());

            for (int i = 0; i < all.size(); i++) {
                int root = uf.find(i);
                List<Integer> ownGroup = indexGroups.get(root);
                if (ownGroup == null || ownGroup.size() != 1) continue;

                double bestScore = 0D;
                Integer bestRoot = null;
                for (Integer candidateRoot : strongRoots) {
                    if (candidateRoot == root) continue;
                    List<Integer> memberIndexes = indexGroups.get(candidateRoot);
                    double score = weakClusterSimilarity(all.get(i), memberIndexes, all, stats);
                    if (score > bestScore) {
                        bestScore = score;
                        bestRoot = candidateRoot;
                    }
                }

                if (bestRoot != null && bestScore >= WEAK_THRESHOLD) {
                    uf.union(i, bestRoot);
                    changed = true;
                }
            }
        } while (changed && rounds < 3);
    }

    private static double weakClusterSimilarity(Candidate candidate,
                                                List<Integer> memberIndexes,
                                                List<Candidate> all,
                                                CorpusStats stats) {
        double maxPair = 0D;
        boolean hasUsefulSharedBigram = false;
        int weakDfLimit = Math.max(8, Math.max(2, all.size() / 6));

        for (Integer idx : memberIndexes) {
            Candidate member = all.get(idx);
            maxPair = Math.max(maxPair, similarity(candidate, member, stats));

            Set<String> shared = new HashSet<>(candidate.bigrams);
            shared.retainAll(member.bigrams);
            for (String gram : shared) {
                if (stats.df(gram) <= weakDfLimit) {
                    hasUsefulSharedBigram = true;
                    break;
                }
            }
        }

        // 只有共享一个在当前热榜语料里相对少见的二元词组时，才允许弱关联加入。
        // 防止仅凭“最新”“发布”等高频片段误合并。
        if (!hasUsefulSharedBigram) return 0D;

        return Math.max(maxPair, 0.30D);
    }

    private static Candidate chooseAnchor(List<Candidate> group, CorpusStats stats) {
        return group.stream().max(Comparator
                .comparingDouble((Candidate c) -> clusterAffinity(c, group, stats))
                .thenComparingInt(c -> safeScore(c.item))
                .thenComparingInt(c -> -safeRank(c.item)))
                .orElse(group.get(0));
    }

    private static Map<String, CandidateScore> chooseBestPerPlatform(List<Candidate> group, CorpusStats stats) {
        Map<String, CandidateScore> bestByPlatform = new HashMap<>();
        for (Candidate c : group) {
            double score = clusterAffinity(c, group, stats);
            CandidateScore old = bestByPlatform.get(c.platform);
            if (old == null
                    || score > old.score
                    || (Math.abs(score - old.score) < 0.0001D && safeRank(c.item) < safeRank(old.candidate.item))) {
                bestByPlatform.put(c.platform, new CandidateScore(c, score));
            }
        }
        return bestByPlatform;
    }

    private static double clusterAffinity(Candidate candidate, List<Candidate> group, CorpusStats stats) {
        if (group.size() <= 1) return 1D;
        double sum = 0D;
        int count = 0;
        for (Candidate other : group) {
            if (candidate == other) continue;
            sum += similarity(candidate, other, stats);
            count++;
        }
        return count == 0 ? 1D : sum / count;
    }

    /**
     * V1.2 标题：优先从同一事件簇的多个标题中自动找共同关键词/短语。
     * 不维护“台风、特朗普、手机”等人工热点词库。
     */
    private static String buildClusterTitle(List<Candidate> group, Candidate anchor, CorpusStats stats) {
        Map<String, Set<Integer>> phraseSupport = new HashMap<>();
        for (int i = 0; i < group.size(); i++) {
            String title = group.get(i).normalizedTitle;
            Set<String> seen = new HashSet<>();
            int maxLen = Math.min(6, title.length());
            for (int len = 2; len <= maxLen; len++) {
                for (int start = 0; start + len <= title.length(); start++) {
                    String phrase = title.substring(start, start + len);
                    if (!containsHan(phrase) || !seen.add(phrase)) continue;
                    phraseSupport.computeIfAbsent(phrase, k -> new HashSet<>()).add(i);
                }
            }
        }

        List<PhraseScore> phrases = phraseSupport.entrySet().stream()
                .filter(e -> e.getValue().size() >= 2)
                .map(e -> new PhraseScore(
                        e.getKey(),
                        e.getValue().size() * e.getKey().length() * stats.idf(e.getKey())))
                .sorted(Comparator
                        .comparingDouble((PhraseScore x) -> x.score).reversed()
                        .thenComparingInt((PhraseScore x) -> x.phrase.length()).reversed())
                .collect(Collectors.toList());

        if (phrases.isEmpty()) return anchor.item.getTitle();

        String first = phrases.get(0).phrase;
        // 长度达到 4 的共同短语通常已经足够表达事件，如“国内手机销量”“台风红色预警”。
        if (first.length() >= 4) return first;

        // 对“白海豚”这种 3 字事件锚点，再尝试寻找一个共同上下文词，如“台风”，组合成“台风白海豚”。
        for (int i = 1; i < phrases.size(); i++) {
            String second = phrases.get(i).phrase;
            if (first.contains(second) || second.contains(first)) continue;
            String combined = combineFromOriginalOrder(group, first, second);
            if (combined != null && combined.length() <= 8) return combined;
        }
        return first;
    }

    private static String combineFromOriginalOrder(List<Candidate> group, String a, String b) {
        String best = null;
        for (Candidate c : group) {
            String title = c.normalizedTitle;
            int ia = title.indexOf(a);
            int ib = title.indexOf(b);
            if (ia < 0 || ib < 0) continue;
            int start = Math.min(ia, ib);
            int end = Math.max(ia + a.length(), ib + b.length());
            String span = title.substring(start, end);
            if (best == null || span.length() < best.length()) best = span;
        }
        return best;
    }

    private static boolean containsHan(String s) {
        for (int i = 0; i < s.length(); i++) {
            Character.UnicodeScript script = Character.UnicodeScript.of(s.charAt(i));
            if (script == Character.UnicodeScript.HAN) return true;
        }
        return false;
    }

    private static double similarity(Candidate a, Candidate b, CorpusStats stats) {
        String x = a.normalizedTitle;
        String y = b.normalizedTitle;
        if (x.equals(y)) return 1D;

        if (x.length() >= 4 && y.length() >= 4 && (x.contains(y) || y.contains(x))) {
            double ratio = (double) Math.min(x.length(), y.length()) / Math.max(x.length(), y.length());
            return 0.75D + 0.25D * ratio;
        }

        double weightedJaccard = weightedJaccard(a.ngrams, b.ngrams, stats);
        double dice = dice(a.bigrams, b.bigrams);
        String lcs = longestCommonSubstring(x, y);
        double lcsRatio = lcs.isEmpty() ? 0D : (double) lcs.length() / Math.min(x.length(), y.length());

        double base = 0.50D * weightedJaccard + 0.30D * dice + 0.20D * lcsRatio;

        // 当前热榜语料中少见的 3+ 字公共短语可视作“事件锚点”。
        // 例如“白海豚”“国内手机销量”“台风红色预警”。
        if (lcs.length() >= 3) {
            int rareLimit = Math.max(6, Math.max(2, stats.documentCount / 8));
            if (stats.containsDf(lcs) <= rareLimit) {
                double anchorBoost = Math.min(0.82D, 0.58D + (lcs.length() - 3) * 0.05D);
                base = Math.max(base, anchorBoost);
            }
        }
        return Math.min(1D, base);
    }

    private static double weightedJaccard(Set<String> a, Set<String> b, CorpusStats stats) {
        Set<String> union = new HashSet<>(a);
        union.addAll(b);
        if (union.isEmpty()) return 0D;

        Set<String> intersection = new HashSet<>(a);
        intersection.retainAll(b);

        double unionWeight = 0D;
        for (String gram : union) unionWeight += stats.idf(gram);
        double intersectionWeight = 0D;
        for (String gram : intersection) intersectionWeight += stats.idf(gram);
        return unionWeight == 0D ? 0D : intersectionWeight / unionWeight;
    }

    private static double dice(Set<String> a, Set<String> b) {
        if (a.isEmpty() || b.isEmpty()) return 0D;
        Set<String> intersection = new HashSet<>(a);
        intersection.retainAll(b);
        return (double) (2 * intersection.size()) / (a.size() + b.size());
    }

    private static String longestCommonSubstring(String a, String b) {
        if (a.isEmpty() || b.isEmpty()) return "";
        int[] dp = new int[b.length() + 1];
        int max = 0;
        int end = 0;
        for (int i = 1; i <= a.length(); i++) {
            for (int j = b.length(); j >= 1; j--) {
                if (a.charAt(i - 1) == b.charAt(j - 1)) {
                    dp[j] = dp[j - 1] + 1;
                    if (dp[j] > max) {
                        max = dp[j];
                        end = i;
                    }
                } else {
                    dp[j] = 0;
                }
            }
        }
        return max == 0 ? "" : a.substring(end - max, end);
    }

    private static String normalize(String title) {
        String s = title.toLowerCase(Locale.ROOT).trim();
        s = s.replace("#", "").replace("【", "").replace("】", "");
        return NOISE.matcher(s).replaceAll("");
    }

    private static Set<String> ngrams(String s, int min, int max) {
        Set<String> set = new HashSet<>();
        for (int n = min; n <= max; n++) {
            if (s.length() < n) continue;
            for (int i = 0; i + n <= s.length(); i++) {
                set.add(s.substring(i, i + n));
            }
        }
        return set;
    }

    private static int safeScore(HotItemDTO item) {
        return item.getNormalizedHotScore() == null ? 0 : item.getNormalizedHotScore();
    }

    private static int safeRank(HotItemDTO item) {
        return item.getRank() == null ? Integer.MAX_VALUE : item.getRank();
    }

    private static double round(double value) {
        return Math.round(value * 1000D) / 1000D;
    }

    private static final class Candidate {
        final String platform;
        final HotItemDTO item;
        final String normalizedTitle;
        final Set<String> bigrams;
        final Set<String> ngrams;

        Candidate(String platform, HotItemDTO item, String normalizedTitle) {
            this.platform = platform;
            this.item = item;
            this.normalizedTitle = normalizedTitle;
            this.bigrams = ngrams(normalizedTitle, 2, 2);
            this.ngrams = ngrams(normalizedTitle, 2, 3);
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

    private static final class PhraseScore {
        final String phrase;
        final double score;

        PhraseScore(String phrase, double score) {
            this.phrase = phrase;
            this.score = score;
        }
    }

    private static final class CorpusStats {
        final int documentCount;
        final List<Candidate> all;
        final Map<String, Integer> df = new HashMap<>();
        final Map<String, Integer> containsDfCache = new HashMap<>();

        CorpusStats(List<Candidate> all) {
            this.all = all;
            this.documentCount = all.size();
            for (Candidate c : all) {
                for (String gram : c.ngrams) {
                    df.merge(gram, 1, Integer::sum);
                }
            }
        }

        int df(String gram) {
            return df.getOrDefault(gram, 0);
        }

        int containsDf(String phrase) {
            return containsDfCache.computeIfAbsent(phrase, p -> {
                int count = 0;
                for (Candidate c : all) {
                    if (c.normalizedTitle.contains(p)) count++;
                }
                return count;
            });
        }

        double idf(String gram) {
            int count = df(gram);
            if (count == 0) count = containsDf(gram);
            return Math.log((documentCount + 1D) / (count + 1D)) + 1D;
        }
    }

    private static final class UnionFind {
        final int[] parent;
        final int[] size;

        UnionFind(int n) {
            parent = new int[n];
            size = new int[n];
            for (int i = 0; i < n; i++) {
                parent[i] = i;
                size[i] = 1;
            }
        }

        int find(int x) {
            if (parent[x] != x) parent[x] = find(parent[x]);
            return parent[x];
        }

        void union(int a, int b) {
            int x = find(a), y = find(b);
            if (x == y) return;
            if (size[x] < size[y]) {
                int tmp = x;
                x = y;
                y = tmp;
            }
            parent[y] = x;
            size[x] += size[y];
        }
    }
}
