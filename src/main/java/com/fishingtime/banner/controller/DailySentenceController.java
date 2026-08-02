package com.fishingtime.banner.controller;

import com.fishingtime.common.dto.ApiResponse;
import com.fishingtime.banner.dto.DailySentenceDTO;
import com.fishingtime.banner.service.DailySentenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 每日一句 API
 *
 * GET /api/daily-sentence/random — 随机返回一条启用状态的句子
 */
@RestController
@RequestMapping("/api/daily-sentence")
@RequiredArgsConstructor
public class DailySentenceController {

    private final DailySentenceService dailySentenceService;

    @GetMapping("/random")
    public ApiResponse<DailySentenceDTO> random() {
        return ApiResponse.success(dailySentenceService.getRandom());
    }
}
