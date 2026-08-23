package com.fishingtime.qa.service;

import com.fishingtime.common.dto.ErrorCode;
import com.fishingtime.common.exception.BusinessException;
import com.fishingtime.qa.domain.QaCategory;
import com.fishingtime.qa.domain.QaQuestion;
import com.fishingtime.qa.domain.QaQuestionOption;
import com.fishingtime.qa.domain.QaUserAnswer;
import com.fishingtime.qa.dto.QaAnswerPage;
import com.fishingtime.qa.dto.QaCategoryVO;
import com.fishingtime.qa.dto.QaHistoryItem;
import com.fishingtime.qa.dto.QaNextResponse;
import com.fishingtime.qa.dto.QaProfileStatsVO;
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
        // 上线题可答；提交者可答自己的待审题（status=0）
        boolean isOwner = q != null && q.getCreatorId() != null && q.getCreatorId().equals(userId);
        if (q == null || !(q.getStatus() == 1 || (q.getStatus() == 0 && isOwner))) {
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

    /** 我的回答历史（分页，最近优先；含当前同选择比例与是否多数派） */
    public QaAnswerPage history(Long userId, int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(pageSize, 1), 50);
        int offset = (safePage - 1) * safeSize;
        List<QaUserAnswer> answers = answerMapper.selectByUserPage(userId, offset, safeSize);
        long total = answerMapper.countByUser(userId);

        List<QaHistoryItem> items = new ArrayList<>(answers.size());
        for (QaUserAnswer a : answers) {
            QaHistoryItem item = new QaHistoryItem();
            item.setQuestionId(a.getQuestionId());
            item.setOptionId(a.getOptionId());
            item.setAnsweredAt(a.getCreatedAt());

            QaQuestion q = questionMapper.selectById(a.getQuestionId());
            List<QaQuestionOption> options = optionMapper.selectByQuestionId(a.getQuestionId());
            if (q == null || options.isEmpty()) {
                // 题目已下线：历史保留，展示占位，不再可投票
                item.setQuestion(q != null ? q.getContent() : "（题目已下线）");
                item.setMyAnswer("");
                item.setSameRate(null);
                item.setMajority(false);
                items.add(item);
                continue;
            }
            QaQuestionOption mine = options.stream()
                    .filter(o -> o.getId().equals(a.getOptionId()))
                    .findFirst()
                    .orElse(null);
            int totalAnswers = q.getAnswerCount() != null ? q.getAnswerCount() : 0;
            int myVotes = mine != null && mine.getVoteCount() != null ? mine.getVoteCount() : 0;
            int maxVote = options.stream()
                    .mapToInt(o -> o.getVoteCount() == null ? 0 : o.getVoteCount())
                    .max().orElse(0);

            item.setQuestion(q.getContent());
            item.setMyAnswer(mine != null ? mine.getContent() : "");
            item.setSameRate(totalAnswers > 0 ? Math.round(myVotes * 100.0f / totalAnswers) : 0);
            item.setMajority(maxVote > 0 && myVotes == maxVote);
            items.add(item);
        }
        return new QaAnswerPage(items, total, safePage, safeSize);
    }

    /** 我的瞅瞅统计：回答数 + 大众派题数/比例/称号（并列任选其一即算多数派；动态计算） */
    public QaProfileStatsVO profileStats(Long userId) {
        List<QaUserAnswer> answers = answerMapper.selectByUser(userId);
        int answerCount = answers.size();
        if (answerCount == 0) {
            return new QaProfileStatsVO(0, 0, 0, null);
        }
        int majority = 0;
        for (QaUserAnswer a : answers) {
            List<QaQuestionOption> options = optionMapper.selectByQuestionId(a.getQuestionId());
            if (options.isEmpty()) {
                continue; // 已下线题无法判定，不计入多数派
            }
            int maxVote = options.stream()
                    .mapToInt(o -> o.getVoteCount() == null ? 0 : o.getVoteCount())
                    .max().orElse(0);
            boolean isMajority = options.stream()
                    .filter(o -> o.getVoteCount() != null && o.getVoteCount() == maxVote)
                    .anyMatch(o -> o.getId().equals(a.getOptionId()));
            if (isMajority) {
                majority++;
            }
        }
        int rate = Math.round(majority * 100.0f / answerCount);
        return new QaProfileStatsVO(answerCount, majority, rate, titleFor(rate));
    }

    private String titleFor(int rate) {
        if (rate >= 80) return "随大流选手";
        if (rate >= 65) return "大众派";
        if (rate >= 45) return "有点自己的想法";
        if (rate >= 30) return "少数派";
        return "你确实不太一样";
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
