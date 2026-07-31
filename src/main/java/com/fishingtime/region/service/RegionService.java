package com.fishingtime.region.service;

import com.fishingtime.region.dto.RegionDTO;
import com.fishingtime.region.dto.RegionImportDTO;

import java.util.List;

/**
 * 地区服务接口
 */
public interface RegionService {

    /** 按 adcode 查询单条 */
    RegionDTO getByAdcode(String adcode);

    /** 分页查询（名称模糊） */
    List<RegionDTO> page(String name, int page, int size);

    /** 按上级 adcode 查询子级 */
    List<RegionDTO> children(String parentAdcode);

    /** 批量导入 */
    int importBatch(List<RegionImportDTO> items);

    /** 总数（辅助） */
    long count(String name);
}
