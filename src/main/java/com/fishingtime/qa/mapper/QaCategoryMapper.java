package com.fishingtime.qa.mapper;

import com.fishingtime.qa.domain.QaCategory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 「瞅瞅」分类 Mapper
 */
@Mapper
public interface QaCategoryMapper {

    List<QaCategory> selectEnabled();

    List<QaCategory> selectAll();

    QaCategory selectById(@Param("id") Long id);

    QaCategory selectByCode(@Param("code") String code);

    void insert(QaCategory category);

    void update(QaCategory category);

    void deleteById(@Param("id") Long id);
}
