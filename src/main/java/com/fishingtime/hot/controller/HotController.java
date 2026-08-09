package com.fishingtime.hot.controller;

import com.fishingtime.hot.dto.HotItemDTO;
import com.fishingtime.hot.dto.SimilarHotClusterDTO;
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
     * 返回当前缓存中至少被两个不同平台同时关注的热点。
     * 每个热点簇最多展示三个平台，不足三个不补齐。
     */
    @GetMapping("/similar/clusters")
    public Map<String, Object> getSimilarClusters() {
        List<SimilarHotClusterDTO> data = hotSimilarityService.cluster(hotService.getAllHotSnapshot());
        Map<String, Object> resp = new HashMap<>();
        resp.put("code", 200);
        resp.put("message", "success");
        resp.put("data", data);
        return resp;
    }
}
