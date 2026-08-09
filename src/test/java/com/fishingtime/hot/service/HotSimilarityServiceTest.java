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
    void shouldClusterSameEventAcrossSameAndDifferentPlatforms() {
        Map<String, List<HotItemDTO>> data = Map.of(
                "toutiao", List.of(
                        item(1, "白海豚10级风圈", 10000),
                        item(4, "白海豚预计将在浙江苍南到三门一带登陆", 7408),
                        item(7, "台风白海豚到哪了", 5488),
                        item(8, "中央气象台升级发布台风红色预警", 4966)
                ),
                "baidu", List.of(
                        item(2, "台风白海豚登陆浙江最新消息", 9000),
                        item(15, "暑期旅游消费持续升温", 4000)
                ),
                "weibo", List.of(
                        item(25, "台风白海豚红色预警持续发布", 7000)
                )
        );

        List<SimilarHotClusterDTO> result = service.cluster(data);

        assertFalse(result.isEmpty());
        SimilarHotClusterDTO cluster = result.stream()
                .filter(x -> x.getTitle().contains("白海豚") || x.getTitle().contains("台风"))
                .findFirst()
                .orElseThrow();

        assertTrue(cluster.getSourceCount() >= 2);
        assertTrue(cluster.getItems().size() >= 2);
        assertTrue(cluster.getItems().size() <= 3);
    }

    @Test
    void shouldNotExposeEventSeenOnlyOnOnePlatform() {
        Map<String, List<HotItemDTO>> data = Map.of(
                "toutiao", List.of(
                        item(1, "白海豚10级风圈", 10000),
                        item(4, "白海豚预计将在浙江登陆", 7408),
                        item(7, "台风白海豚到哪了", 5488)
                ),
                "baidu", List.of(
                        item(1, "国内手机销量排行榜", 9000)
                )
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
