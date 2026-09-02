package com.fishingtime.hot.service;

import com.fishingtime.hot.dto.HotItemDTO;
import com.fishingtime.hot.dto.SimilarHotClusterDTO;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventFingerprintClusterServiceTest {

    private final EventFingerprintClusterService service = new EventFingerprintClusterService();

    @Test
    void shouldPreCluster260And2600HardFingerprintBeforeGreedySemanticCluster() {
        Map<String, List<HotItemDTO>> data = Map.of(
                "baidu", List.of(
                        item(1, "高校回应学生退学问题", 10000),
                        item(8, "260名学生申请退学 涉及2600万元学费", 8000)
                ),
                "weibo", List.of(
                        item(12, "高校回应260名学生退学 退还2600万元", 7000)
                ),
                "zhihu", List.of(
                        item(3, "大学生退学后该如何重新规划", 9000)
                )
        );

        List<SimilarHotClusterDTO> result = service.cluster(data);

        SimilarHotClusterDTO target = result.stream()
                .filter(cluster -> cluster.getItems().stream()
                        .anyMatch(entry -> entry.getHotItem().getTitle().contains("2600")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("260 + 2600 事件应进入共同热点"));

        assertEquals(2, target.getSourceCount(), "硬数字指纹应锁定为百度+微博两个来源");
        assertEquals(2, target.getItems().size());
        assertTrue(target.getItems().stream()
                .allMatch(entry -> entry.getHotItem().getTitle().contains("260")
                        && entry.getHotItem().getTitle().contains("2600")),
                "普通的学生/退学语义标题不能吞掉 260 + 2600 硬事件簇");
    }

    @Test
    void shouldNotTreatSingleSharedNumberAsHardEvidence() {
        Map<String, List<HotItemDTO>> data = Map.of(
                "baidu", List.of(item(1, "260名学生参加校园招聘", 8000)),
                "weibo", List.of(item(2, "260名游客抵达景区", 7000))
        );

        List<SimilarHotClusterDTO> result = service.cluster(data);

        assertTrue(result.isEmpty(), "只有一个共同数字时不能强行聚为共同热点");
    }

    private static HotItemDTO item(int rank, String title, int score) {
        return HotItemDTO.builder()
                .rank(rank)
                .title(title)
                .normalizedHotScore(score)
                .build();
    }
}
