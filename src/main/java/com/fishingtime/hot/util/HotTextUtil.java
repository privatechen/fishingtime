package com.fishingtime.hot.util;

import com.fishingtime.hot.dto.HotItemDTO;
import com.huaban.analysis.jieba.JiebaSegmenter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** 热榜文本处理公共工具。 */
public final class HotTextUtil {

    public static final JiebaSegmenter SEGMENTER = new JiebaSegmenter();
    public static final Pattern CLEAN_TO_SPACE = Pattern.compile("[^\\p{IsHan}a-zA-Z0-9]+");
    public static final Pattern PURE_NUMBER = Pattern.compile("^\\d+$");

    public static final Set<String> STOP_WORDS = Set.of(
            "的", "了", "是", "在", "和", "与", "及", "或", "将", "把", "被", "对", "到", "从", "为", "有", "也",
            "又", "都", "就", "还", "已", "已是", "正在", "进行", "表示", "回应", "称", "发布", "最新", "目前", "今日",
            "如何", "怎么", "为什么", "什么", "哪些", "一个", "一名", "网友", "现场", "消息", "视频", "相关", "正式",
            "预计", "再次", "持续", "成为", "引发", "引热议", "来了", "去哪", "哪了", "能否", "可能", "开始", "这个",
            "评价", "认为", "关于", "发生", "出现", "情况", "方面"
    );

    private HotTextUtil() {}

    public static String normalize(String title) {
        String value = title.toLowerCase(Locale.ROOT)
                .replace('＃', '#')
                .replace("#", " ")
                .replace("【", " ")
                .replace("】", " ");
        return CLEAN_TO_SPACE.matcher(value).replaceAll(" ").trim().replaceAll("\\s+", " ");
    }

    /**
     * 分词并过滤噪声。
     *
     * V1.6 数字策略：不再无差别丢弃所有纯数字。3 位及以上数字通常具有较强事件辨识度
     * （如排名 260/2600、航班号/编号等），保留参与 IDF 相似度计算；1~2 位数字仍过滤，
     * 避免日期、年龄、数量等常见短数字造成误聚类。高频年份即使被保留，也会因 IDF 较低
     * 自然降权，不会单独成为强匹配依据。
     */
    public static List<String> segment(String normalizedText) {
        List<String> result = new ArrayList<>();
        for (String token : SEGMENTER.sentenceProcess(normalizedText)) {
            if (token == null) continue;
            String value = token.trim().toLowerCase(Locale.ROOT);
            if (value.isEmpty() || STOP_WORDS.contains(value)) continue;
            if (PURE_NUMBER.matcher(value).matches() && value.length() < 3) continue;
            if (value.length() == 1 && containsHan(value)) continue;
            result.add(value);
        }
        return result;
    }

    public static List<String> tokenize(String title) {
        return segment(normalize(title));
    }

    public static boolean containsHan(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.UnicodeScript.of(value.charAt(i)) == Character.UnicodeScript.HAN) return true;
        }
        return false;
    }

    public static double rankWeight(int rank) {
        if (rank <= 0 || rank == Integer.MAX_VALUE) return 0D;
        return 1D / (Math.log(rank + 1D) / Math.log(2D));
    }

    public static int safeRank(HotItemDTO item) {
        return item == null || item.getRank() == null ? Integer.MAX_VALUE : item.getRank();
    }

    public static int safeScore(HotItemDTO item) {
        return item == null || item.getNormalizedHotScore() == null ? 0 : item.getNormalizedHotScore();
    }

    public static double round(double value) {
        return Math.round(value * 1000D) / 1000D;
    }

    public static double idf(int documentCount, int frequency) {
        return Math.log((documentCount + 1D) / (frequency + 1D)) + 1D;
    }

    public static double tokenWeight(int documentCount, int frequency, String token) {
        return idf(documentCount, frequency) * Math.min(Math.max(token.length(), 1), 4);
    }
}
