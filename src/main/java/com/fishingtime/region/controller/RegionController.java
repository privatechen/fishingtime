package com.fishingtime.region.controller;

import com.fishingtime.common.dto.ApiResponse;
import com.fishingtime.region.dto.RegionDTO;
import com.fishingtime.region.dto.RegionImportDTO;
import com.fishingtime.region.service.RegionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 地区 API
 *
 * GET  /api/region/{adcode}          — 查询单条
 * GET  /api/region                   — 分页查询（?page=&size=&name=）
 * GET  /api/region/children/{adcode} — 查询子级
 * POST /api/region/import            — 批量导入
 */
@Slf4j
@RestController
@RequestMapping("/api/region")
@RequiredArgsConstructor
public class RegionController {

    private final RegionService regionService;

    /** 查询单条 */
    @GetMapping("/{adcode}")
    public ApiResponse<RegionDTO> getByAdcode(@PathVariable String adcode) {
        return ApiResponse.success(regionService.getByAdcode(adcode));
    }

    /** 分页查询 */
    @GetMapping
    public ApiResponse<List<RegionDTO>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String name) {
        return ApiResponse.success(regionService.page(name, page, size));
    }

    /** 查询子级 */
    @GetMapping("/children/{adcode}")
    public ApiResponse<List<RegionDTO>> children(@PathVariable String adcode) {
        return ApiResponse.success(regionService.children(adcode));
    }

    /** 批量导入 */
    @PostMapping("/import")
    public ApiResponse<Integer> importBatch(@RequestBody List<RegionImportDTO> items) {
        int count = regionService.importBatch(items);
        return ApiResponse.success(count);
    }
}
