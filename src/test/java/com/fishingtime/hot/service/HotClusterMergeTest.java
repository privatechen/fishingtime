package com.fishingtime.hot.service;

import com.fishingtime.hot.dto.HotItemDTO;
import com.fishingtime.hot.dto.SimilarHotClusterDTO;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 簇间合并（优化③）行为测试。
 *
 * 正例：同一事件被增量聚类拆成两组子簇、两组仅共享"高 IDF 稀有实体词"时，应合并回 1 簇；
 * 反例：两组仅共享"低 IDF 常见泛词"（如"手机"）时，不得合并。
 */
class HotClusterMergeTest {

    private final HotSimilarityService service = new HotSimilarityService();

    @Test
    void shouldMergeSplitClustersSharingOnlyRareEntityWord() {
        Map<String, List<HotItemDTO>> data = new HashMap<>();
        // 事件组 A：toutiao + baidu，共享"白海豚/路径/实时/预警"
        data.put("toutiao", List.of(
                item(1, "白海豚路径预报发布", 10000),
                item(3, "白海豚路径实时更新", 8000)));
        data.put("baidu", List.of(
                item(2, "白海豚红色预警升级", 9000),
                item(5, "白海豚预警实时播报", 7000)));
        // 事件组 B：weibo + zhihu，共享"白海豚/台风"
        data.put("weibo", List.of(
                item(4, "台风白海豚路径走向", 8500),
                item(6, "台风白海豚中心风力", 6000)));
        data.put("zhihu", List.of(
                item(7, "白海豚台风实时路径", 5500),
                item(8, "白海豚台风登陆时间", 5000)));
        // 噪声：4 个平台各 5 条无关标题，压低"白海豚"在全语料的 df 占比，
        // 使其成为高 IDF 稀有词，触发 rareCoreBridge 桥接合并
        String[][] noise = {
                {"歌手新专辑发布", "球队客场取胜", "景区免票政策", "新书销量登顶", "航展门票开售"},
                {"电影节开幕红毯", "地铁新线通车", "餐厅米其林上榜", "宠物领养活动", "极光观赏攻略"},
                {"大学开放校园日", "航天器成功对接", "博物馆夜场开放", "马拉松报名开启", "咖啡新品上市"},
                {"高铁增开列车", "演唱会加场", "古装剧开拍", "全民健身日", "科技展闭幕"},
        };
        String[] noisePlatforms = {"hupu", "sina", "qq", "wangyi"};
        for (int p = 0; p < noisePlatforms.length; p++) {
            List<HotItemDTO> items = new ArrayList<>();
            for (int i = 0; i < noise[p].length; i++) {
                items.add(item(100 + p * 10 + i, noise[p][i], 3000));
            }
            data.put(noisePlatforms[p], items);
        }

        List<SimilarHotClusterDTO> result = service.cluster(data);

        assertEquals(1, result.size(), "拆散的同一事件应合并为 1 簇");
        assertEquals(4, result.get(0).getSourceCount());
        assertTrue(result.get(0).getTitle().contains("白海豚")
                || result.get(0).getTitle().contains("海豚"));
    }

    @Test
    void shouldNotMergeClustersSharingOnlyCommonWord() {
        // 组X（toutiao+weibo）共享"手机/销量/排行榜"→ 内部聚成 1 簇；
        // 组Y（baidu+zhihu）共享"手机/新品/发布会"→ 内部聚成 1 簇；
        // 组间仅共享常见泛词"手机"（IDF 低），不触发合并 → 保持 2 簇。
        Map<String, List<HotItemDTO>> data = Map.of(
                "toutiao", List.of(item(1, "手机销量排行榜正式发布", 9000)),
                "weibo", List.of(item(2, "手机销量排行榜最新出炉", 8000)),
                "baidu", List.of(item(3, "手机新品发布会今日开启", 7000)),
                "zhihu", List.of(item(4, "手机新品发布会亮点曝光", 6000))
        );

        List<SimilarHotClusterDTO> result = service.cluster(data);

        assertEquals(2, result.size());
    }

    private static HotItemDTO item(int rank, String title, int score) {
        return HotItemDTO.builder()
                .rank(rank)
                .title(title)
                .normalizedHotScore(score)
                .build();
    }
}
