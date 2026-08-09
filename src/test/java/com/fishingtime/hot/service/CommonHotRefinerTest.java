package com.fishingtime.hot.service;

import com.fishingtime.hot.dto.HotItemDTO;
import com.fishingtime.hot.dto.PlatformHotItemDTO;
import com.fishingtime.hot.dto.SimilarHotClusterDTO;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CommonHotRefinerTest {

    private final CommonHotRefiner refiner = new CommonHotRefiner();

    @Test
    void shouldKeepTyphoonWhenAtLeastTwoKeywordsAreSharedAcrossPlatforms() {
        SimilarHotClusterDTO raw = cluster(
                entry("weibo", 8, "台风白海豚红色预警持续发布"),
                entry("baidu", 11, "台风白海豚登陆浙江最新路径"),
                entry("toutiao", 7, "台风白海豚到哪了")
        );

        Map<String, List<HotItemDTO>> corpus = Map.of(
                "weibo", List.of(item(8, "台风白海豚红色预警持续发布")),
                "baidu", List.of(item(11, "台风白海豚登陆浙江最新路径")),
                "toutiao", List.of(item(7, "台风白海豚到哪了")),
                "zhihu", List.of(item(1, "暑期旅游目的地推荐"))
        );

        List<SimilarHotClusterDTO> result = refiner.refine(List.of(raw), corpus);

        assertEquals(1, result.size());
        String title = result.get(0).getTitle();
        assertTrue(title.contains("台风"));
        assertTrue(title.contains("白海豚"));
        assertTrue(title.contains(" "), "关键词之间应该用空格分隔");
        assertEquals(3, result.get(0).getSourceCount());
    }

    @Test
    void shouldDropBroadClusterWhenPlatformsShareOnlyOneKeyword() {
        SimilarHotClusterDTO raw = cluster(
                entry("weibo", 30, "中国男篮备战亚洲杯"),
                entry("baidu", 25, "中国记者采访国际赛事"),
                entry("hupu", 10, "中国足球新赛季名单")
        );

        Map<String, List<HotItemDTO>> corpus = Map.of(
                "weibo", List.of(item(30, "中国男篮备战亚洲杯")),
                "baidu", List.of(item(25, "中国记者采访国际赛事")),
                "hupu", List.of(item(10, "中国足球新赛季名单"))
        );

        List<SimilarHotClusterDTO> result = refiner.refine(List.of(raw), corpus);

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldNotCountThirdPlatformThatDoesNotHitTwoClusterKeywords() {
        SimilarHotClusterDTO raw = cluster(
                entry("weibo", 8, "台风白海豚红色预警"),
                entry("baidu", 11, "台风白海豚登陆浙江"),
                entry("hupu", 21, "上海台风天气讨论")
        );

        Map<String, List<HotItemDTO>> corpus = Map.of(
                "weibo", List.of(item(8, "台风白海豚红色预警")),
                "baidu", List.of(item(11, "台风白海豚登陆浙江")),
                "hupu", List.of(item(21, "上海台风天气讨论"))
        );

        List<SimilarHotClusterDTO> result = refiner.refine(List.of(raw), corpus);

        assertEquals(1, result.size());
        assertEquals(2, result.get(0).getSourceCount());
        assertEquals(2, result.get(0).getItems().size());
        assertTrue(result.get(0).getItems().stream().noneMatch(x -> "hupu".equals(x.getPlatform())));
    }

    private static SimilarHotClusterDTO cluster(PlatformHotItemDTO... entries) {
        return SimilarHotClusterDTO.builder()
                .title("raw")
                .sourceCount(entries.length)
                .items(List.of(entries))
                .build();
    }

    private static PlatformHotItemDTO entry(String platform, int rank, String title) {
        return PlatformHotItemDTO.builder()
                .platform(platform)
                .hotItem(item(rank, title))
                .similarityScore(0.8D)
                .build();
    }

    private static HotItemDTO item(int rank, String title) {
        return HotItemDTO.builder()
                .rank(rank)
                .title(title)
                .normalizedHotScore(10000 - rank)
                .build();
    }
}
