package com.fishingtime.hot.controller;

import com.fishingtime.hot.dto.HotItemDTO;
import com.fishingtime.hot.service.HotService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 热榜 API
 *
 * 返回结构：
 * {
 *   "code": 200,
 *   "message": "success",
 *   "updateTime": "2026-07-30T17:05:00",
 *   "nextRefreshTime": "2026-07-30T17:15:00",
 *   "data": [...]
 * }
 */
@Slf4j
@RestController
@RequestMapping("/api/hot")
@RequiredArgsConstructor
public class HotController {

    private final HotService hotService;

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
}
