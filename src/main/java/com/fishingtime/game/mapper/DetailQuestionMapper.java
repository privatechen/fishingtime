package com.fishingtime.game.mapper;

import com.fishingtime.game.domain.DetailQuestion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 《细节》题库 Mapper
 */
@Mapper
public interface DetailQuestionMapper {

    /** 某图片下启用状态的题目（V1 每图 10 道，抽 6 用） */
    List<DetailQuestion> selectEnabledByImageKey(@Param("imageKey") String imageKey);

    /** 启用题目涉及的全部图片标识（作为本局可玩图片池） */
    List<String> selectEnabledImageKeys();
}
