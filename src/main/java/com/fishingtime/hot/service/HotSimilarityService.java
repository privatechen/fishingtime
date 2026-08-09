package com.fishingtime.hot.service;

import com.fishingtime.hot.dto.HotItemDTO;
import com.fishingtime.hot.dto.PlatformHotItemDTO;
import com.fishingtime.hot.dto.SimilarHotClusterDTO;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 跨平台热点相似聚类。
 *
 * V1 不依赖人工热点词库，也不引入 ES/向量库：
 * 1. 标题自动标准化；
 * 2. 中文字符 bigram + ASCII token 自动特征；
 * 3. Jaccard + Dice 计算相似度；
 * 4. 只比较不同平台；
 * 5. 并查集形成热点簇；
 * 6. 至少 2 个平台才输出，每个平台只保留最优一条，最多展示 3 条。
 */
@Service
public class HotSimilarityService {

    private static final double MATCH_THRESHOLD = 0.48D;
    private static final Pattern NOISE = Pattern.compile("[\\p{P}\\p{S}\\s]+");

    public List<SimilarHotClusterDTO> cluster(Map<String, List<HotItemDTO>> platformData) {
        List<Candidate> all = new ArrayList<>();
        platformData.forEach((platform, items) -> {
            if (items == null) return;
            for (HotItemDTO item : items) {
                if (item == null || item.getTitle() == null || item.getTitle().trim().isEmpty()) continue;
                all.add(new Candidate(platform, item, normalize(item.getTitle())));
            }
        });

        UnionFind uf = new UnionFind(all.size());
        for (int i = 0; i < all.size(); i++) {
            for (int j = i + 1; j < all.size(); j++) {
                Candidate a = all.get(i);
                Candidate b = all.get(j);
                if (a.platform.equals(b.platform)) continue;
                double score = similarity(a.normalizedTitle, b.normalizedTitle);
                if (score >= MATCH_THRESHOLD) uf.union(i, j);
            }
        }

        Map<Integer, List<Candidate>> groups = new HashMap<>();
        for (int i = 0; i < all.size(); i++) {
            groups.computeIfAbsent(uf.find(i), k -> new ArrayList<>()).add(all.get(i));
        }

        List<SimilarHotClusterDTO> result = new ArrayList<>();
        for (List<Candidate> group : groups.values()) {
            Set<String> platforms = group.stream().map(c -> c.platform).collect(Collectors.toSet());
            if (platforms.size() < 2) continue;

            Candidate anchor = group.stream().max(Comparator
                    .comparingInt((Candidate c) -> safeScore(c.item))
                    .thenComparingInt(c -> -safeRank(c.item))).orElse(group.get(0));

            Map<String, CandidateScore> bestByPlatform = new HashMap<>();
            for (Candidate c : group) {
                double score = c == anchor ? 1D : similarity(anchor.normalizedTitle, c.normalizedTitle);
                CandidateScore old = bestByPlatform.get(c.platform);
                if (old == null || score > old.score) bestByPlatform.put(c.platform, new CandidateScore(c, score));
            }

            List<PlatformHotItemDTO> items = bestByPlatform.values().stream()
                    .sorted(Comparator
                            .comparingDouble((CandidateScore x) -> x.score).reversed()
                            .thenComparing((CandidateScore x) -> safeScore(x.candidate.item), Comparator.reverseOrder()))
                    .limit(3)
                    .map(x -> PlatformHotItemDTO.builder()
                            .platform(x.candidate.platform)
                            .hotItem(x.candidate.item)
                            .similarityScore(round(x.score))
                            .build())
                    .collect(Collectors.toList());

            if (items.size() >= 2) {
                result.add(SimilarHotClusterDTO.builder()
                        .title(anchor.item.getTitle())
                        .sourceCount(platforms.size())
                        .items(items)
                        .build());
            }
        }

        result.sort(Comparator
                .comparingInt(SimilarHotClusterDTO::getSourceCount).reversed()
                .thenComparingInt(c -> c.getItems().stream()
                        .map(PlatformHotItemDTO::getHotItem)
                        .mapToInt(HotSimilarityService::safeScore).max().orElse(0)).reversed());
        return result;
    }

    private static String normalize(String title) {
        String s = title.toLowerCase(Locale.ROOT).trim();
        s = s.replace("#", "").replace("【", "").replace("】", "");
        return NOISE.matcher(s).replaceAll("");
    }

    /**
     * 不需要人工配置热点词。中文短标题采用字符 bigram，英文/数字也自然参与匹配。
     * 包含关系用于处理“某事件”与“某事件最新进展”这种常见热榜标题。
     */
    static double similarity(String a, String b) {
        if (a.equals(b)) return 1D;
        if (a.length() >= 4 && b.length() >= 4 && (a.contains(b) || b.contains(a))) {
            double ratio = (double) Math.min(a.length(), b.length()) / Math.max(a.length(), b.length());
            return 0.75D + 0.25D * ratio;
        }
        Set<String> x = bigrams(a);
        Set<String> y = bigrams(b);
        if (x.isEmpty() || y.isEmpty()) return 0D;
        int intersection = 0;
        for (String token : x) if (y.contains(token)) intersection++;
        int union = x.size() + y.size() - intersection;
        double jaccard = union == 0 ? 0D : (double) intersection / union;
        double dice = (double) (2 * intersection) / (x.size() + y.size());
        return 0.55D * jaccard + 0.45D * dice;
    }

    private static Set<String> bigrams(String s) {
        Set<String> set = new HashSet<>();
        if (s.length() == 1) {
            set.add(s);
            return set;
        }
        for (int i = 0; i < s.length() - 1; i++) set.add(s.substring(i, i + 2));
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
        Candidate(String platform, HotItemDTO item, String normalizedTitle) {
            this.platform = platform;
            this.item = item;
            this.normalizedTitle = normalizedTitle;
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

    private static final class UnionFind {
        final int[] parent;
        UnionFind(int n) {
            parent = new int[n];
            for (int i = 0; i < n; i++) parent[i] = i;
        }
        int find(int x) {
            if (parent[x] != x) parent[x] = find(parent[x]);
            return parent[x];
        }
        void union(int a, int b) {
            int x = find(a), y = find(b);
            if (x != y) parent[y] = x;
        }
    }
}
