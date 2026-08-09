package com.fishingtime.hot.controller;

import com.fishingtime.hot.dto.HotItemDTO;
import com.fishingtime.hot.dto.SimilarHotClusterDTO;
import com.fishingtime.hot.service.CommonHotRefiner;
import com.fishingtime.hot.service.HotService;
import com.fishingtime.hot.service.HotSimilarityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 热榜 API。 */
@Slf4j
@RestController
@RequestMapping("/api/hot")
@RequiredArgsConstructor
public class HotController {

    private final HotService hotService;
    private final HotSimilarityService hotSimilarityService;
    private final CommonHotRefiner commonHotRefiner;

    @GetMapping("/{platform}")
    public Map<String, Object> getHot(@PathVariable String platform) {
        HotService.HotResult result = hotService.getHot(platform);
        List<HotItemDTO> data = result.getData();

        Map<String, Object> resp = new HashMap<>();
        resp.put("code", 200);
        resp.put("message", "success");
        resp.put("updateTime", result.getUpdateTime());
        resp.put("nextRefreshTime", result.getNextRefreshTime());

        if (data.isEmpty()) {
            resp.put("code", 404);
            resp.put("message", "暂无 " + platform + " 热榜数据");
        }
        resp.put("data", data);
        return resp;
    }

    /**
     * 返回全网共同热点：
     * 1. 先做事件聚类；
     * 2. 再做严格关键词过滤，至少 2 个跨平台共同关键词才展示；
     * 3. 每个计入的平台自身也必须命中至少 2 个共同关键词。
     */
    @GetMapping("/similar/clusters")
    public Map<String, Object> getSimilarClusters() {
        Map<String, List<HotItemDTO>> snapshot = hotService.getAllHotSnapshot();
        List<SimilarHotClusterDTO> raw = hotSimilarityService.cluster(snapshot);
        List<SimilarHotClusterDTO> data = commonHotRefiner.refine(raw, snapshot);

        Map<String, Object> resp = new HashMap<>();
        resp.put("code", 200);
        resp.put("message", "success");
        resp.put("data", data);
        return resp;
    }
}
