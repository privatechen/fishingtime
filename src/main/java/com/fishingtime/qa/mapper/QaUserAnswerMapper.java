package com.fishingtime.qa.mapper;

import com.fishingtime.qa.domain.QaUserAnswer;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 「瞅瞅」用户回答 Mapper
 */
@Mapper
public interface QaUserAnswerMapper {

    QaUserAnswer selectByUserAndQuestion(@Param("userId") Long userId, @Param("questionId") Long questionId);

    /** 用户回答历史（最近优先） */
    List<QaUserAnswer> selectByUser(@Param("userId") Long userId);

    /** 用户回答历史分页（最近优先） */
    List<QaUserAnswer> selectByUserPage(@Param("userId") Long userId,
                                        @Param("offset") int offset,
                                        @Param("limit") int limit);

    /** 用户回答总数 */
    long countByUser(@Param("userId") Long userId);

    /** 插入；UNIQUE(user_id, question_id) 冲突时抛 DuplicateKeyException（幂等靠它） */
    void insert(QaUserAnswer answer);
}
