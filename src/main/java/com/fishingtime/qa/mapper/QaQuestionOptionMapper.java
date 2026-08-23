package com.fishingtime.qa.mapper;

import com.fishingtime.qa.domain.QaQuestionOption;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 「瞅瞅」问题选项 Mapper
 */
@Mapper
public interface QaQuestionOptionMapper {

    List<QaQuestionOption> selectByQuestionId(@Param("questionId") Long questionId);

    void insertBatch(@Param("list") List<QaQuestionOption> list);

    void update(QaQuestionOption option);

    void deleteByQuestionId(@Param("questionId") Long questionId);

    /** 投票：仅当 option 属于该 question 时 +1（防越权刷票） */
    int incrementVoteCount(@Param("id") Long id, @Param("questionId") Long questionId);
}
