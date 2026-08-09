package com.fishingtime.hot.service;

import com.fishingtime.hot.dto.HotItemDTO;
import com.fishingtime.hot.dto.SimilarHotClusterDTO;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HotSimilarityServiceTest {

    private final HotSimilarityService service = new HotSimilarityService();

    @Test
    void shouldClusterTyphoonEventAndPreferItInConsensusRanking() {
        Map<String, List<HotItemDTO>> data = Map.of(
                "toutiao", List.of(
                        item(1, "白海豚10级风圈", 10000),
                        item(4, "白海豚预计将在浙江苍南到三门一带登陆", 7408),
                        item(7, "台风白海豚到哪了", 5488),
                        item(8, "中央气象台升级发布台风红色预警", 4966),
                        item(10, "王艺迪晋级女单四强", 4200)
                ),
                "baidu", List.of(
                        item(2, "台风白海豚登陆浙江最新消息", 9000),
                        item(3, "上半年国内手机销量排行榜", 8800),
                        item(9, "王艺迪24张本美和晋级4强", 7000)
                ),
                "weibo", List.of(
                        item(5, "台风白海豚红色预警持续发布", 7000),
                        item(2, "上半年国内手机销量TOP30出炉", 8500)
                )
        );

        List<SimilarHotClusterDTO> result = service.cluster(data);

        assertFalse(result.isEmpty());
        SimilarHotClusterDTO typhoon = result.stream()
                .filter(x -> x.getTitle().contains("白海豚") || x.getTitle().contains("台风"))
                .findFirst()
                .orElseThrow();

        assertEquals(3, typhoon.getSourceCount());
        assertTrue(typhoon.getItems().size() >= 2);
        assertTrue(typhoon.getItems().size() <= 3);
        assertEquals(typhoon, result.get(0), "三个平台共同关注且排名靠前的台风事件应优先展示");
    }

    @Test
    void shouldBuildTitleFromWholeTokensNotBrokenCharacterFragments() {
        Map<String, List<HotItemDTO>> data = Map.of(
                "baidu", List.of(item(3, "上半年国内手机销量排行榜", 9000)),
                "weibo", List.of(item(2, "上半年国内手机销量TOP30出炉", 9200))
        );

        List<SimilarHotClusterDTO> result = service.cluster(data);

        assertEquals(1, result.size());
        String title = result.get(0).getTitle();
        // 标题不得是"年国内手机"这种字符断词拼接。
        // 用 startsWith 而非 contains：完整标题"上半年国内手机…"也含该子串，但以"上半年"开头。
        assertFalse(title.startsWith("年国内手机"));
        assertTrue(title.contains("手机") || title.contains("销量"));
    }

    @Test
    void shouldNotCreateBrokenChineseKeywordLikeZhunZhongGuo() {
        Map<String, List<HotItemDTO>> data = Map.of(
                "baidu", List.of(item(7, "以军士兵把枪口对准中国记者", 8000)),
                "toutiao", List.of(item(2, "以军士兵持枪对准中国记者", 9000))
        );

        List<SimilarHotClusterDTO> result = service.cluster(data);

        assertEquals(1, result.size());
        assertFalse(result.get(0).getTitle().startsWith("准中国"));
        assertTrue(result.get(0).getTitle().contains("以军")
                || result.get(0).getTitle().contains("中国")
                || result.get(0).getTitle().contains("记者"));
    }

    @Test
    void shouldNotExposeEventSeenOnlyOnOnePlatform() {
        Map<String, List<HotItemDTO>> data = Map.of(
                "toutiao", List.of(
                        item(1, "白海豚10级风圈", 10000),
                        item(4, "白海豚预计将在浙江登陆", 7408),
                        item(7, "台风白海豚到哪了", 5488)
                ),
                "baidu", List.of(item(1, "国内手机销量排行榜", 9000))
        );

        List<SimilarHotClusterDTO> result = service.cluster(data);
        assertTrue(result.stream().noneMatch(x -> x.getTitle().contains("白海豚")));
    }

    @Test
    void shouldKeepUnrelatedHotTopicsSeparated() {
        Map<String, List<HotItemDTO>> data = Map.of(
                "toutiao", List.of(item(1, "白海豚10级风圈", 10000)),
                "baidu", List.of(item(1, "上半年国内手机销量排行榜", 10000)),
                "weibo", List.of(item(1, "某地演唱会正式官宣", 10000))
        );

        List<SimilarHotClusterDTO> result = service.cluster(data);
        assertTrue(result.isEmpty());
    }

    private static HotItemDTO item(int rank, String title, int score) {
        return HotItemDTO.builder()
                .rank(rank)
                .title(title)
                .normalizedHotScore(score)
                .build();
    }
}
