package com.fishingtime.qa.service;

import com.fishingtime.common.dto.ErrorCode;
import com.fishingtime.common.exception.BusinessException;
import com.fishingtime.qa.domain.QaCategory;
import com.fishingtime.qa.domain.QaQuestion;
import com.fishingtime.qa.domain.QaQuestionOption;
import com.fishingtime.qa.domain.QaQuestionSubmit;
import com.fishingtime.qa.dto.QaSubmitAdminVO;
import com.fishingtime.qa.dto.QaSubmitRequest;
import com.fishingtime.qa.mapper.QaCategoryMapper;
import com.fishingtime.qa.mapper.QaQuestionMapper;
import com.fishingtime.qa.mapper.QaQuestionOptionMapper;
import com.fishingtime.qa.mapper.QaQuestionSubmitMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 「瞅瞅」用户出题投稿服务
 *
 * - 用户投稿进入待审队列（status=0）
 * - 管理员审核：通过 → 生成正式题目（上线）+ 标记通过；驳回 → 记录原因
 */
@Service
@RequiredArgsConstructor
public class QaSubmitService {

    private final QaQuestionSubmitMapper submitMapper;
    private final QaQuestionMapper questionMapper;
    private final QaQuestionOptionMapper optionMapper;
    private final QaCategoryMapper categoryMapper;
    private final ObjectMapper objectMapper;

    /** 用户投稿（待审） */
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
        for (QaSubmitRequest.QaSubmitOption o : req.getOptions()) {
            if (o.getContent() == null || o.getContent().isBlank()) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "选项内容不能为空");
            }
        }
        String optionsJson;
        try {
            optionsJson = objectMapper.writeValueAsString(req.getOptions());
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "选项序列化失败");
        }

        QaQuestionSubmit s = new QaQuestionSubmit();
        s.setUserId(userId);
        s.setCategoryId(req.getCategoryId());
        s.setContent(req.getContent().trim());
        s.setDescription(req.getDescription());
        s.setOptionsJson(optionsJson);
        s.setStatus(0);
        submitMapper.insert(s);
    }

    /** 管理后台列表（status 为空则全部） */
    public List<QaSubmitAdminVO> list(Integer status) {
        List<QaQuestionSubmit> subs = submitMapper.selectByStatus(status);
        List<QaSubmitAdminVO> list = new ArrayList<>(subs.size());
        for (QaQuestionSubmit s : subs) {
            list.add(toAdminVO(s));
        }
        return list;
    }

    /** 审核通过：生成正式题目并上线，投稿标记已通过 */
    @Transactional(rollbackFor = Exception.class)
    public void approve(Long id) {
        QaQuestionSubmit s = requirePending(id);
        List<QaSubmitRequest.QaSubmitOption> opts;
        try {
            opts = objectMapper.readValue(s.getOptionsJson(),
                    new TypeReference<List<QaSubmitRequest.QaSubmitOption>>() {});
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "投稿选项解析失败");
        }

        QaQuestion q = new QaQuestion();
        q.setCategoryId(s.getCategoryId());
        q.setContent(s.getContent());
        q.setDescription(s.getDescription());
        q.setStatus(1);
        q.setRecommendScore(BigDecimal.ONE);
        q.setSortOrder(0);
        q.setPublishedAt(LocalDateTime.now());
        questionMapper.insert(q);

        List<QaQuestionOption> options = new ArrayList<>();
        int order = 1;
        for (QaSubmitRequest.QaSubmitOption o : opts) {
            QaQuestionOption qo = new QaQuestionOption();
            qo.setQuestionId(q.getId());
            qo.setContent(o.getContent());
            qo.setIcon(o.getIcon());
            qo.setSortOrder(order++);
            qo.setVoteCount(0);
            options.add(qo);
        }
        if (!options.isEmpty()) {
            optionMapper.insertBatch(options);
        }
        submitMapper.updateStatus(id, 1, null);
    }

    /** 审核驳回 */
    public void reject(Long id, String reason) {
        requirePending(id);
        submitMapper.updateStatus(id, 2, reason);
    }

    private QaQuestionSubmit requirePending(Long id) {
        QaQuestionSubmit s = submitMapper.selectById(id);
        if (s == null || s.getStatus() != 0) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "投稿不存在或已处理");
        }
        return s;
    }

    private QaSubmitAdminVO toAdminVO(QaQuestionSubmit s) {
        QaSubmitAdminVO vo = new QaSubmitAdminVO();
        vo.setId(s.getId());
        vo.setUserId(s.getUserId());
        vo.setCategoryId(s.getCategoryId());
        QaCategory c = categoryMapper.selectById(s.getCategoryId());
        vo.setCategoryName(c != null ? c.getName() : "");
        vo.setContent(s.getContent());
        vo.setDescription(s.getDescription());
        vo.setStatus(s.getStatus());
        vo.setRejectReason(s.getRejectReason());
        vo.setCreatedAt(s.getCreatedAt());
        try {
            vo.setOptions(objectMapper.readValue(s.getOptionsJson(),
                    new TypeReference<List<QaSubmitRequest.QaSubmitOption>>() {}));
        } catch (Exception e) {
            vo.setOptions(new ArrayList<>());
        }
        return vo;
    }
}
