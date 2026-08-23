package com.fishingtime.qa.mapper;

import com.fishingtime.qa.domain.QaQuestion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 「瞅瞅」问题 Mapper
 */
@Mapper
public interface QaQuestionMapper {

    QaQuestion selectById(@Param("id") Long id);

    /** 分类下下一道未答题（排序靠前优先） */
    QaQuestion selectNextInCategory(@Param("categoryId") Long categoryId, @Param("userId") Long userId);

    /** 推荐：全库未答题，recommend_score + 随机扰动 */
    QaQuestion selectNextRecommend(@Param("userId") Long userId);

    /** 某分类题目（管理后台，含全部状态；categoryId 为空则全部） */
    List<QaQuestion> selectForAdmin(@Param("categoryId") Long categoryId);

    void insert(QaQuestion question);

    void update(QaQuestion question);

    void updateStatus(@Param("id") Long id, @Param("status") Integer status);

    void deleteById(@Param("id") Long id);

    void incrementAnswerCount(@Param("id") Long id);

    void incrementViewCount(@Param("id") Long id);

    /** 某分类题目数（管理后台） */
    long countByCategory(@Param("categoryId") Long categoryId);
}
