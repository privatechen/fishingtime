package com.fishingtime.qa.mapper;

import com.fishingtime.qa.domain.QaQuestionSubmit;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 「瞅瞅」用户出题投稿 Mapper
 */
@Mapper
public interface QaQuestionSubmitMapper {

    void insert(QaQuestionSubmit submit);

    QaQuestionSubmit selectById(@Param("id") Long id);

    List<QaQuestionSubmit> selectByStatus(@Param("status") Integer status);

    void updateStatus(@Param("id") Long id,
                      @Param("status") Integer status,
                      @Param("rejectReason") String rejectReason);
}
