package com.fishingtime.hot;

import com.fishingtime.hot.dto.HotItemDTO;
import com.fishingtime.hot.util.HotScoreParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * HotScoreParser 单元测试
 *
 * 覆盖 PRD 中所有输入格式及归一化逻辑
 */
class HotScoreParserTest {

    // ═══════════════════════════════════════════
    // parse() — 原始热度值解析
    // ═══════════════════════════════════════════

    @Test
    @DisplayName("100万热度 → 1000000")
    void test100wan() {
        assertEquals(1_000_000, HotScoreParser.parse("100万热度"));
    }

    @Test
    @DisplayName("1.2万热度 → 12000")
    void test1_2wan() {
        assertEquals(12_000, HotScoreParser.parse("1.2万热度"));
    }

    @Test
    @DisplayName("9999 → 9999")
    void test9999() {
        assertEquals(9_999, HotScoreParser.parse("9999"));
    }

    @Test
    @DisplayName("1000000 → 1000000")
    void test1000000() {
        assertEquals(1_000_000, HotScoreParser.parse("1000000"));
    }

    @Test
    @DisplayName("剧集 542463 → 542463")
    void testDrama542463() {
        assertEquals(542_463, HotScoreParser.parse("剧集 542463"));
    }

    @Test
    @DisplayName("电影 8560 → 8560")
    void testMovie8560() {
        assertEquals(8_560, HotScoreParser.parse("电影 8560"));
    }

    @Test
    @DisplayName("1.5亿热度 → 150000000")
    void test1_5yi() {
        assertEquals(150_000_000, HotScoreParser.parse("1.5亿热度"));
    }

    @Test
    @DisplayName("8500 → 8500")
    void test8500() {
        assertEquals(8_500, HotScoreParser.parse("8500"));
    }

    @Test
    @DisplayName("null → 0")
    void testNull() {
        assertEquals(0, HotScoreParser.parse(null));
    }

    @Test
    @DisplayName("空字符串 → 0")
    void testEmpty() {
        assertEquals(0, HotScoreParser.parse(""));
    }

    @Test
    @DisplayName("无数字 → 0")
    void testNoNumber() {
        assertEquals(0, HotScoreParser.parse("--"));
    }

    @Test
    @DisplayName("负值 → 0")
    void testNegative() {
        assertEquals(0, HotScoreParser.parse("-100"));
    }

    @Test
    @DisplayName("英文逗号分隔 → 1000000")
    void testComma() {
        assertEquals(1_000_000, HotScoreParser.parse("1,000,000"));
    }

    @Test
    @DisplayName("中文逗号分隔 → 1000000")
    void testChineseComma() {
        assertEquals(1_000_000, HotScoreParser.parse("1，000，000热度"));
    }

    @Test
    @DisplayName("千单位 → 1000")
    void testQian() {
        assertEquals(1_000, HotScoreParser.parse("1千"));
    }

    // ═══════════════════════════════════════════
    // normalizeScores() — 榜单归一化
    // ═══════════════════════════════════════════

    @Test
    @DisplayName("归一化：最大值为基准，其余按比例计算")
    void testNormalizeScores() {
        List<HotItemDTO> items = new ArrayList<>();
        items.add(HotItemDTO.builder().hotScore("8000").build());
        items.add(HotItemDTO.builder().hotScore("4000").build());
        items.add(HotItemDTO.builder().hotScore("2000").build());
        items.add(HotItemDTO.builder().hotScore("1000").build());

        HotScoreParser.normalizeScores(items);

        assertEquals(10_000, items.get(0).getNormalizedHotScore()); // 8000/8000×10000
        assertEquals(5_000, items.get(1).getNormalizedHotScore());  // 4000/8000×10000
        assertEquals(2_500, items.get(2).getNormalizedHotScore());  // 2000/8000×10000
        assertEquals(1_250, items.get(3).getNormalizedHotScore());  // 1000/8000×10000
    }

    @Test
    @DisplayName("归一化：全部为0 → 全部0")
    void testNormalizeAllZero() {
        List<HotItemDTO> items = new ArrayList<>();
        items.add(HotItemDTO.builder().hotScore("0").build());
        items.add(HotItemDTO.builder().hotScore("0").build());

        HotScoreParser.normalizeScores(items);

        assertEquals(0, items.get(0).getNormalizedHotScore());
        assertEquals(0, items.get(1).getNormalizedHotScore());
    }

    @Test
    @DisplayName("归一化：无法解析的输入 → 全部0")
    void testNormalizeInvalid() {
        List<HotItemDTO> items = new ArrayList<>();
        items.add(HotItemDTO.builder().hotScore("--").build());
        items.add(HotItemDTO.builder().hotScore(null).build());

        HotScoreParser.normalizeScores(items);

        assertEquals(0, items.get(0).getNormalizedHotScore());
        assertEquals(0, items.get(1).getNormalizedHotScore());
    }

    @Test
    @DisplayName("归一化：空列表不报错")
    void testNormalizeEmpty() {
        HotScoreParser.normalizeScores(new ArrayList<>());
        // 不抛异常即通过
    }

    @Test
    @DisplayName("归一化：null 不报错")
    void testNormalizeNull() {
        HotScoreParser.normalizeScores(null);
        // 不抛异常即通过
    }

    @Test
    @DisplayName("归一化：中文单位混合")
    void testNormalizeChineseUnits() {
        List<HotItemDTO> items = new ArrayList<>();
        items.add(HotItemDTO.builder().hotScore("100万热度").build());     // 1000000
        items.add(HotItemDTO.builder().hotScore("剧集 542463").build());  // 542463
        items.add(HotItemDTO.builder().hotScore("8000").build());         // 8000

        HotScoreParser.normalizeScores(items);

        assertEquals(10_000, items.get(0).getNormalizedHotScore()); // 1000000/1000000×10000
        assertEquals(5_425, items.get(1).getNormalizedHotScore());  // round(542463/1000000×10000) = 5425
        assertEquals(80, items.get(2).getNormalizedHotScore());     // 8000/1000000×10000 = 80
    }
}
