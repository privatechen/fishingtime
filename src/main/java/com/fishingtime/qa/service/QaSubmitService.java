package com.fishingtime.qa.service;

import com.fishingtime.common.dto.ErrorCode;
import com.fishingtime.common.exception.BusinessException;
import com.fishingtime.qa.domain.QaCategory;
import com.fishingtime.qa.domain.QaQuestion;
import com.fishingtime.qa.domain.QaQuestionOption;
import com.fishingtime.qa.domain.QaUserAnswer;
import com.fishingtime.qa.dto.QaMySubmitVO;
import com.fishingtime.qa.dto.QaOptionVO;
import com.fishingtime.qa.dto.QaSubmitRequest;
import com.fishingtime.qa.mapper.QaCategoryMapper;
import com.fishingtime.qa.mapper.QaQuestionMapper;
import com.fishingtime.qa.mapper.QaQuestionOptionMapper;
import com.fishingtime.qa.mapper.QaUserAnswerMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 「瞅瞅」用户出题投稿服务
 *
 * 投稿直接创建一道真实题目（status=0 待审 + creator_id），提交者可看可答；
 * 管理员审核通过后 status=1 上线，驳回 status=2 + 原因。
 */
@Service
@RequiredArgsConstructor
public class QaSubmitService {

    private final QaQuestionMapper questionMapper;
    private final QaQuestionOptionMapper optionMapper;
    private final QaCategoryMapper categoryMapper;
    private final QaUserAnswerMapper answerMapper;

    /** 用户投稿：创建待审题目 + 选项 */
    public void submit(Long userId, QaSubmitRequest req) {
        if (req == null || req.getContent() == null || req.getContent().isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "请填写问题内容");
        }
        if (req.getCategoryId() == null || categoryMapper.selectById(req.getCategoryId()) == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "请选择分类");
        }
        if (req.getOptions() == null || req.getOptions().size() < 2 || req.getOptions().size() > 6) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "请提供 2~6 个选项");
        }

        QaQuestion q = new QaQuestion();
        q.setCategoryId(req.getCategoryId());
        q.setContent(req.getContent().trim());
        q.setDescription(req.getDescription());
        q.setStatus(0);           // 待审
        q.setCreatorId(userId);   // 投稿人
        q.setRecommendScore(BigDecimal.ONE);
        q.setSortOrder(0);
        questionMapper.insert(q);

        List<QaQuestionOption> options = new ArrayList<>();
        int order = 1;
        for (QaSubmitRequest.QaSubmitOption o : req.getOptions()) {
            if (o.getContent() == null || o.getContent().isBlank()) {
                continue;
            }
            QaQuestionOption qo = new QaQuestionOption();
            qo.setQuestionId(q.getId());
            qo.setContent(o.getContent().trim());
            qo.setIcon(o.getIcon());
            qo.setSortOrder(order++);
            qo.setVoteCount(0);
            options.add(qo);
        }
        if (!options.isEmpty()) {
            optionMapper.insertBatch(options);
        }
    }

    /** 我的投稿列表 */
    public List<QaMySubmitVO> mine(Long userId) {
        List<QaQuestion> qs = questionMapper.selectMineByUser(userId);
        List<QaMySubmitVO> list = new ArrayList<>(qs.size());
        for (QaQuestion q : qs) {
            list.add(toMyVO(userId, q));
        }
        return list;
    }

    private QaMySubmitVO toMyVO(Long userId, QaQuestion q) {
        QaMySubmitVO vo = new QaMySubmitVO();
        vo.setQuestionId(q.getId());
        vo.setCategoryId(q.getCategoryId());
        QaCategory c = categoryMapper.selectById(q.getCategoryId());
        vo.setCategoryName(c != null ? c.getName() : "");
        vo.setContent(q.getContent());
        vo.setStatus(q.getStatus());
        vo.setRejectReason(q.getRejectReason());
        vo.setAnswerCount(q.getAnswerCount());

        QaUserAnswer mine = answerMapper.selectByUserAndQuestion(userId, q.getId());
        vo.setAnswered(mine != null);
        if (mine != null) {
            vo.setMyOptionId(mine.getOptionId());
        }

        List<QaQuestionOption> options = optionMapper.selectByQuestionId(q.getId());
        int total = q.getAnswerCount() != null ? q.getAnswerCount() : 0;
        List<QaOptionVO> optionVOs = new ArrayList<>(options.size());
        for (QaQuestionOption o : options) {
            QaOptionVO ov = new QaOptionVO();
            ov.setId(o.getId());
            ov.setContent(o.getContent());
            ov.setIcon(o.getIcon());
            ov.setSortOrder(o.getSortOrder());
            if (mine != null) {
                ov.setVoteCount(o.getVoteCount());
                ov.setPercent(total > 0 ? Math.round(o.getVoteCount() * 1000.0 / total) / 10.0 : null);
            }
            optionVOs.add(ov);
        }
        vo.setOptions(optionVOs);
        return vo;
    }
}
