package com.fishingtime.region.mapper;

import com.fishingtime.region.domain.Region;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 地区 Mapper — SQL 全在 XML 中
 */
@Mapper
public interface RegionMapper {

    Region selectByAdcode(@Param("adcode") String adcode);

    List<Region> selectList(@Param("name") String name,
                            @Param("offset") int offset,
                            @Param("size") int size);

    long count(@Param("name") String name);

    List<Region> selectByParent(@Param("parentAdcode") String parentAdcode);

    void insertBatch(@Param("list") List<Region> regions);
}
