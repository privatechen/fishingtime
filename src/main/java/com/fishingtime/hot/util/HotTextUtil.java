package com.fishingtime.hot.util;

import com.fishingtime.hot.dto.HotItemDTO;
import com.huaban.analysis.jieba.JiebaSegmenter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 热榜文本处理公共工具。
 *
 * 统一两个聚类服务（HotSimilarityService / CommonHotRefiner）的分词、清洗、IDF、排名辅助逻辑，
 * 消除重复实现与停用词差异。纯静态工具，零 Spring 依赖，两个服务可保持无参构造。
 *
 * 并发安全：Jieba 的 WordDictionary 是 JVM 级静态单例（类初始化加载词典，之后只读），
 * sentenceProcess 每次调用新建线程私有 Segment，并发读安全，共享单个静态实例即可。
 */
public final class HotTextUtil {

    /** Jieba 分词器 — 共享单例，并发读安全（见类注释） */
    public static final JiebaSegmenter SEGMENTER = new JiebaSegmenter();

    /** 非汉字/字母/数字一律替换为空格（用于标题清洗） */
    public static final Pattern CLEAN_TO_SPACE = Pattern.compile("[^\\p{IsHan}a-zA-Z0-9]+");

    /** 纯数字 token（如排名、年份），不作为关键词 */
    public static final Pattern PURE_NUMBER = Pattern.compile("^\\d+$");

    /**
     * 停用词（并集）：CommonHotRefiner 的集合 + HotSimilarityService 独有词。
     * 只放通用语气/功能词，不维护具体热点实体词。
     */
    public static final Set<String> STOP_WORDS = Set.of(
            "的", "了", "是", "在", "和", "与", "及", "或", "将", "把", "被", "对", "到", "从", "为", "有", "也",
            "又", "都", "就", "还", "已", "已是", "正在", "进行", "表示", "回应", "称", "发布", "最新", "目前", "今日",
            "如何", "怎么", "为什么", "什么", "哪些", "一个", "一名", "网友", "现场", "消息", "视频", "相关", "正式",
            "预计", "再次", "持续", "成为", "引发", "引热议", "来了", "去哪", "哪了", "能否", "可能", "开始", "这个",
            "评价", "认为", "关于", "发生", "出现", "情况", "方面"
    );

    private HotTextUtil() {}

    /**
     * 标题规范化：转小写、处理 #/【】话题括号、非文字字符替换为空格、空白折叠。
     * 两个聚类服务统一使用本方法，保证清洗行为一致。
     */
    public static String normalize(String title) {
        String value = title.toLowerCase(Locale.ROOT)
                .replace('＃', '#')
                .replace("#", " ")
                .replace("【", " ")
                .replace("】", " ");
        return CLEAN_TO_SPACE.matcher(value).replaceAll(" ").trim().replaceAll("\\s+", " ");
    }

    /**
     * 对已规范化的文本分词并过滤停用词/纯数字/单字汉字。
     * 入参应为 normalize(title) 的产物；如需一步到位用 {@link #tokenize(String)}。
     */
    public static List<String> segment(String normalizedText) {
        List<String> result = new ArrayList<>();
        for (String token : SEGMENTER.sentenceProcess(normalizedText)) {
            if (token == null) continue;
            String value = token.trim().toLowerCase(Locale.ROOT);
            if (value.isEmpty() || STOP_WORDS.contains(value) || PURE_NUMBER.matcher(value).matches()) continue;
            if (value.length() == 1 && containsHan(value)) continue;
            result.add(value);
        }
        return result;
    }

    /** 一步到位：标题规范化 + 分词过滤 */
    public static List<String> tokenize(String title) {
        return segment(normalize(title));
    }

    /** 是否包含中文字符 */
    public static boolean containsHan(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.UnicodeScript.of(value.charAt(i)) == Character.UnicodeScript.HAN) return true;
        }
        return false;
    }

    /** 排名 → 权重：排名越靠前权重越高；无效排名返回 0 */
    public static double rankWeight(int rank) {
        if (rank <= 0 || rank == Integer.MAX_VALUE) return 0D;
        return 1D / (Math.log(rank + 1D) / Math.log(2D));
    }

    /** null-safe 排名取值：item 或 rank 为 null 返回 Integer.MAX_VALUE */
    public static int safeRank(HotItemDTO item) {
        return item == null || item.getRank() == null ? Integer.MAX_VALUE : item.getRank();
    }

    /** null-safe 热度取值：normalizedHotScore 为 null 返回 0 */
    public static int safeScore(HotItemDTO item) {
        return item == null || item.getNormalizedHotScore() == null ? 0 : item.getNormalizedHotScore();
    }

    /** 千分位取整（保留 3 位小数） */
    public static double round(double value) {
        return Math.round(value * 1000D) / 1000D;
    }

    /** IDF：词越少见权重越高；docCount/frequency 为 0 时安全返回 1.0 */
    public static double idf(int documentCount, int frequency) {
        return Math.log((documentCount + 1D) / (frequency + 1D)) + 1D;
    }

    /** token 权重 = IDF × 长度权重（长度上限 4） */
    public static double tokenWeight(int documentCount, int frequency, String token) {
        return idf(documentCount, frequency) * Math.min(Math.max(token.length(), 1), 4);
    }
}
