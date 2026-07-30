package com.fishingtime.hot.controller;

import com.fishingtime.common.dto.ApiResponse;
import com.fishingtime.hot.dto.HotItemDTO;
import com.fishingtime.hot.service.HotService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 热榜 API
 *
 * GET /api/hot/baidu   — 百度热榜
 * GET /api/hot/zhihu   — 知乎热榜（未来）
 * GET /api/hot/weibo   — 微博热搜（未来）
 */
@Slf4j
@RestController
@RequestMapping("/api/hot")
@RequiredArgsConstructor
public class HotController {

    private final HotService hotService;

    @GetMapping("/{platform}")
    public ApiResponse<List<HotItemDTO>> getHot(@PathVariable String platform) {
        List<HotItemDTO> data = hotService.getHot(platform);
        if (data.isEmpty()) {
            return ApiResponse.error(com.fishingtime.common.dto.ErrorCode.NOT_FOUND.getCode(),
                    "暂无 " + platform + " 热榜数据");
        }
        return ApiResponse.success(data);
    }
}
