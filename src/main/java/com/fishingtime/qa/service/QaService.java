package com.fishingtime.qa.service;

import com.fishingtime.common.dto.ErrorCode;
import com.fishingtime.common.exception.BusinessException;
import com.fishingtime.qa.domain.QaCategory;
import com.fishingtime.qa.domain.QaQuestion;
import com.fishingtime.qa.domain.QaQuestionOption;
import com.fishingtime.qa.domain.QaUserAnswer;
import com.fishingtime.qa.dto.QaCategoryVO;
import com.fishingtime.qa.dto.QaHistoryItem;
import com.fishingtime.qa.dto.QaNextResponse;
import com.fishingtime.qa.dto.QaOptionVO;
import com.fishingtime.qa.dto.QaQuestionVO;
import com.fishingtime.qa.mapper.QaCategoryMapper;
import com.fishingtime.qa.mapper.QaQuestionMapper;
import com.fishingtime.qa.mapper.QaQuestionOptionMapper;
import com.fishingtime.qa.mapper.QaUserAnswerMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 「瞅瞅」问答服务
 *
 * - 下一题：分类取未答题（排序优先）；推荐取全库未答题（recommend_score + 随机扰动）
 * - 答题：事务 + UNIQUE(user_id, question_id) 幂等，重复提交返回原答案+最新统计，不重复计数
 * - 校验：option 必须属于当前 question
 */
@Service
@RequiredArgsConstructor
public class QaService {

    private static final String RECOMMEND_CODE = "recommend";

    private final QaCategoryMapper categoryMapper;
    private final QaQuestionMapper questionMapper;
    private final QaQuestionOptionMapper optionMapper;
    private final QaUserAnswerMapper answerMapper;

    public List<QaCategoryVO> categories() {
        return categoryMapper.selectEnabled().stream()
                .map(c -> new QaCategoryVO(c.getId(), c.getCode(), c.getName(), c.getIcon()))
                .collect(Collectors.toList());
    }

    public QaNextResponse next(Long userId, String categoryCode) {
        QaQuestion q;
        if (RECOMMEND_CODE.equalsIgnoreCase(categoryCode)) {
            q = questionMapper.selectNextRecommend(userId);
        } else {
            QaCategory c = categoryMapper.selectByCode(categoryCode);
            if (c == null) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "未知分类");
            }
            q = questionMapper.selectNextInCategory(c.getId(), userId);
        }
        if (q == null) {
            return new QaNextResponse(true, null);
        }
        questionMapper.incrementViewCount(q.getId());
        return new QaNextResponse(false, buildQuestionVO(userId, q, false));
    }

    public QaQuestionVO detail(Long userId, Long questionId) {
        QaQuestion q = questionMapper.selectById(questionId);
        if (q == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "题目不存在");
        }
        questionMapper.incrementViewCount(questionId);
        QaUserAnswer mine = answerMapper.selectByUserAndQuestion(userId, questionId);
        return buildQuestionVO(userId, q, mine != null);
    }

    /** 提交答案：事务 + 幂等；返回最新统计（含本人选择与各选项比例） */
    @Transactional(rollbackFor = Exception.class)
    public QaQuestionVO answer(Long userId, Long questionId, Long optionId) {
        if (optionId == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "请选择选项");
        }
        QaQuestion q = questionMapper.selectById(questionId);
        if (q == null || q.getStatus() != 1) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "题目不可作答");
        }
        List<QaQuestionOption> options = optionMapper.selectByQuestionId(questionId);
        boolean optionBelongs = options.stream().anyMatch(o -> o.getId().equals(optionId));
        if (!optionBelongs) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "选项不属于该题");
        }

        // 幂等：已答过直接返回原答案 + 最新统计，不重复计数
        if (answerMapper.selectByUserAndQuestion(userId, questionId) == null) {
            QaUserAnswer ans = new QaUserAnswer();
            ans.setUserId(userId);
            ans.setQuestionId(questionId);
            ans.setOptionId(optionId);
            try {
                answerMapper.insert(ans);
            } catch (DuplicateKeyException e) {
                // 并发重复提交：另一请求已插入，直接返回结果，不再计数
                return buildQuestionVO(userId, q, true);
            }
            int rows = optionMapper.incrementVoteCount(optionId, questionId);
            if (rows == 0) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "选项不存在");
            }
            questionMapper.incrementAnswerCount(questionId);
            // 同步内存中的 answerCount，供 buildQuestionVO 计算比例
            q.setAnswerCount((q.getAnswerCount() == null ? 0 : q.getAnswerCount()) + 1);
        }
        return buildQuestionVO(userId, q, true);
    }

    public List<QaHistoryItem> history(Long userId) {
        List<QaUserAnswer> answers = answerMapper.selectByUser(userId);
        List<QaHistoryItem> list = new ArrayList<>(answers.size());
        for (QaUserAnswer a : answers) {
            QaQuestion q = questionMapper.selectById(a.getQuestionId());
            if (q == null) {
                continue;
            }
            QaHistoryItem item = new QaHistoryItem();
            item.setQuestionId(q.getId());
            item.setContent(q.getContent());
            item.setOptionId(a.getOptionId());
            optionMapper.selectByQuestionId(a.getQuestionId()).stream()
                    .filter(o -> o.getId().equals(a.getOptionId()))
                    .findFirst()
                    .ifPresent(o -> item.setOptionContent(o.getContent()));
            item.setAnsweredAt(a.getCreatedAt());
            list.add(item);
        }
        return list;
    }

    private QaQuestionVO buildQuestionVO(Long userId, QaQuestion q, boolean showStats) {
        QaQuestionVO vo = new QaQuestionVO();
        vo.setId(q.getId());
        vo.setCategoryId(q.getCategoryId());
        QaCategory c = categoryMapper.selectById(q.getCategoryId());
        vo.setCategoryName(c != null ? c.getName() : "");
        vo.setContent(q.getContent());
        vo.setAnswerCount(q.getAnswerCount());
        vo.setAnswered(showStats);

        List<QaQuestionOption> options = optionMapper.selectByQuestionId(q.getId());
        List<QaOptionVO> optionVOs = new ArrayList<>(options.size());
        for (QaQuestionOption o : options) {
            QaOptionVO ov = new QaOptionVO();
            ov.setId(o.getId());
            ov.setContent(o.getContent());
            ov.setIcon(o.getIcon());
            ov.setSortOrder(o.getSortOrder());
            if (showStats) {
                ov.setVoteCount(o.getVoteCount());
                int total = q.getAnswerCount() != null ? q.getAnswerCount() : 0;
                ov.setPercent(total > 0 ? Math.round(o.getVoteCount() * 1000.0 / total) / 10.0 : null);
            }
            optionVOs.add(ov);
        }
        vo.setOptions(optionVOs);

        if (showStats) {
            QaUserAnswer mine = answerMapper.selectByUserAndQuestion(userId, q.getId());
            if (mine != null) {
                vo.setMyOptionId(mine.getOptionId());
            }
        }
        return vo;
    }
}
