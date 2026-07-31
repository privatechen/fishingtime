package com.fishingtime.region.service.impl;

import com.fishingtime.common.dto.ErrorCode;
import com.fishingtime.common.exception.BusinessException;
import com.fishingtime.region.domain.Region;
import com.fishingtime.region.dto.RegionDTO;
import com.fishingtime.region.dto.RegionImportDTO;
import com.fishingtime.region.mapper.RegionMapper;
import com.fishingtime.region.service.RegionService;
import com.fishingtime.region.util.AdcodeParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 地区服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RegionServiceImpl implements RegionService {

    private final RegionMapper regionMapper;

    @Override
    public RegionDTO getByAdcode(String adcode) {
        Region region = regionMapper.selectByAdcode(adcode);
        if (region == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "地区不存在: " + adcode);
        }
        return toDTO(region);
    }

    @Override
    public List<RegionDTO> page(String name, int page, int size) {
        if (page < 1) page = 1;
        if (size < 1 || size > 100) size = 10;
        int offset = (page - 1) * size;
        return regionMapper.selectList(name, offset, size).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Override
    public List<RegionDTO> children(String parentAdcode) {
        return regionMapper.selectByParent(parentAdcode).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int importBatch(List<RegionImportDTO> items) {
        if (items == null || items.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "导入数据不能为空");
        }

        List<Region> regions = new ArrayList<>(items.size());
        for (RegionImportDTO item : items) {
            if (item.getAdcode() == null || item.getAdcode().isBlank()) continue;

            Region region = new Region();
            region.setName(item.getName());
            region.setAdcode(item.getAdcode());
            region.setCitycode(item.getCitycode());
            region.setLevel(AdcodeParser.parseLevel(item.getAdcode()));
            region.setParentAdcode(AdcodeParser.parseParent(item.getAdcode()));
            regions.add(region);
        }

        if (regions.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "无有效数据");
        }

        // 分批插入，避免 SQL 过长
        int batchSize = 500;
        int total = 0;
        for (int i = 0; i < regions.size(); i += batchSize) {
            List<Region> sub = regions.subList(i, Math.min(i + batchSize, regions.size()));
            regionMapper.insertBatch(sub);
            total += sub.size();
        }
        log.info("[地区] 批量导入完成，共 {} 条", total);
        return total;
    }

    @Override
    public long count(String name) {
        return regionMapper.count(name);
    }

    private RegionDTO toDTO(Region region) {
        RegionDTO dto = new RegionDTO();
        dto.setName(region.getName());
        dto.setAdcode(region.getAdcode());
        dto.setCitycode(region.getCitycode());
        dto.setLevel(region.getLevel());
        dto.setParentAdcode(region.getParentAdcode());
        return dto;
    }
}
