package com.fishingtime.banner.mapper;

import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 每日一句 Mapper
 */
@Mapper
public interface DailySentenceMapper {

    /** 加载所有启用的句子内容（初始化时一次性读入内存） */
    List<String> selectAllEnabledContents();
}
