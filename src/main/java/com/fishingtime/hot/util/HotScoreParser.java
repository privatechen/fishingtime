package com.fishingtime.hot.util;

import com.fishingtime.hot.dto.HotItemDTO;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 热点热度值统一解析器
 *
 * 功能一：将不同平台的原始热度文本转换为统一整数
 * 功能二：对单平台榜单做归一化，计算 normalizedHotScore
 */
public final class HotScoreParser {

    private static final Pattern NUMBER_PATTERN = Pattern.compile("(-?\\d+(?:\\.\\d+)?)");

    private HotScoreParser() {}

    /**
     * 将原始热度文本解析为统一整数。
     * 只做格式清洗、数字提取、单位换算，不做归一化。
     *
     * @param hotScore 原始热度字符串，如 "100万热度"、"剧集 542463"
     * @return 解析后的原始热度值，非法输入返回 0
     */
    public static int parse(String hotScore) {
        if (hotScore == null || hotScore.isBlank()) {
            return 0;
        }

        String text = hotScore.trim()
                .replace(",", "")
                .replace("，", "");

        Matcher matcher = NUMBER_PATTERN.matcher(text);
        if (!matcher.find()) {
            return 0;
        }

        try {
            double number = Double.parseDouble(matcher.group(1));
            double multiplier = resolveMultiplier(text);
            long rawValue = Math.round(number * multiplier);

            if (rawValue <= 0) {
                return 0;
            }
            if (rawValue > Integer.MAX_VALUE) {
                return Integer.MAX_VALUE;
            }
            return (int) rawValue;
        } catch (NumberFormatException | ArithmeticException e) {
            return 0;
        }
    }

    /**
     * 对单个平台的榜单做归一化，计算 normalizedHotScore。
     *
     * 规则：
     *   1. 先解析每个条目的原始热度值
     *   2. 取当前榜单最大原始热度值为 maxRaw
     *   3. normalizedHotScore = round(raw / maxRaw × 10000)
     *   4. 结果限制在 0～10000
     *
     * 每个平台独立计算，互不影响。
     *
     * @param items 同一平台的抓取结果，会修改每个 item 的 normalizedHotScore
     */
    public static void normalizeScores(List<HotItemDTO> items) {
        if (items == null || items.isEmpty()) return;

        // 步骤1：解析所有原始热度值
        int[] rawScores = new int[items.size()];
        int maxRaw = 0;
        for (int i = 0; i < items.size(); i++) {
            int raw = parse(items.get(i).getHotScore());
            rawScores[i] = raw;
            if (raw > maxRaw) {
                maxRaw = raw;
            }
        }

        // 步骤2：如果最大值为 0，所有设 0 并返回
        if (maxRaw <= 0) {
            for (HotItemDTO item : items) {
                item.setNormalizedHotScore(0);
            }
            return;
        }

        // 步骤3：计算 normalizedHotScore
        for (int i = 0; i < items.size(); i++) {
            int calculated = (int) Math.round((double) rawScores[i] / maxRaw * 10000);
            int normalized = Math.max(0, Math.min(calculated, 10000));
            items.get(i).setNormalizedHotScore(normalized);
        }
    }

    private static double resolveMultiplier(String text) {
        if (text.contains("亿")) {
            return 100_000_000D;
        }
        if (text.contains("万")) {
            return 10_000D;
        }
        if (text.contains("千")) {
            return 1_000D;
        }
        return 1D;
    }
}
