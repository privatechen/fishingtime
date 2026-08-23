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

    /** 管理后台列表（categoryId/status 均可空） */
    List<QaQuestion> selectForAdmin(@Param("categoryId") Long categoryId, @Param("status") Integer status);

    /** 我提交的题目（投稿，按时间倒序） */
    List<QaQuestion> selectMineByUser(@Param("userId") Long userId);

    void insert(QaQuestion question);

    void update(QaQuestion question);

    void updateStatus(@Param("id") Long id, @Param("status") Integer status);

    /** 审核驳回：状态置 2 + 记录原因 */
    void updateReject(@Param("id") Long id, @Param("reason") String reason);

    void deleteById(@Param("id") Long id);

    void incrementAnswerCount(@Param("id") Long id);

    void incrementViewCount(@Param("id") Long id);

    /** 某分类题目数（管理后台） */
    long countByCategory(@Param("categoryId") Long categoryId);
}
