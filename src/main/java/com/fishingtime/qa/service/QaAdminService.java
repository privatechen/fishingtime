package com.fishingtime.qa.service;

import com.fishingtime.common.dto.ErrorCode;
import com.fishingtime.common.exception.BusinessException;
import com.fishingtime.qa.domain.QaCategory;
import com.fishingtime.qa.domain.QaQuestion;
import com.fishingtime.qa.domain.QaQuestionOption;
import com.fishingtime.qa.dto.QaCategoryAdminVO;
import com.fishingtime.qa.dto.QaCategorySaveRequest;
import com.fishingtime.qa.dto.QaOptionAdminVO;
import com.fishingtime.qa.dto.QaQuestionAdminVO;
import com.fishingtime.qa.dto.QaQuestionSaveRequest;
import com.fishingtime.qa.mapper.QaCategoryMapper;
import com.fishingtime.qa.mapper.QaQuestionMapper;
import com.fishingtime.qa.mapper.QaQuestionOptionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 「瞅瞅」管理后台服务（分类 + 题目 CRUD）
 */
@Service
@RequiredArgsConstructor
public class QaAdminService {

    private final QaCategoryMapper categoryMapper;
    private final QaQuestionMapper questionMapper;
    private final QaQuestionOptionMapper optionMapper;

    // ────────────── 分类 ──────────────

    public List<QaCategoryAdminVO> listCategories() {
        List<QaCategory> cats = categoryMapper.selectAll();
        List<QaCategoryAdminVO> list = new ArrayList<>(cats.size());
        for (QaCategory c : cats) {
            QaCategoryAdminVO vo = new QaCategoryAdminVO();
            vo.setId(c.getId());
            vo.setCode(c.getCode());
            vo.setName(c.getName());
            vo.setIcon(c.getIcon());
            vo.setSortOrder(c.getSortOrder());
            vo.setStatus(c.getStatus());
            vo.setQuestionCount(questionMapper.countByCategory(c.getId()));
            list.add(vo);
        }
        return list;
    }

    public void saveCategory(QaCategorySaveRequest req) {
        if (req == null || req.getCode() == null || req.getCode().isBlank()
                || req.getName() == null || req.getName().isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "分类编码和名称不能为空");
        }
        QaCategory c = new QaCategory();
        c.setCode(req.getCode().trim());
        c.setName(req.getName().trim());
        c.setIcon(req.getIcon());
        c.setSortOrder(req.getSortOrder() != null ? req.getSortOrder() : 0);
        c.setStatus(req.getStatus() != null ? req.getStatus() : 1);
        if (req.getId() == null) {
            if (categoryMapper.selectByCode(c.getCode()) != null) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "分类编码已存在");
            }
            categoryMapper.insert(c);
        } else {
            c.setId(req.getId());
            categoryMapper.update(c);
        }
    }

    public void deleteCategory(Long id) {
        if (questionMapper.countByCategory(id) > 0) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "该分类下还有题目，不能删除");
        }
        categoryMapper.deleteById(id);
    }

    // ────────────── 题目 ──────────────

    public List<QaQuestionAdminVO> listQuestions(Long categoryId) {
        List<QaQuestion> qs = questionMapper.selectForAdmin(categoryId);
        List<QaQuestionAdminVO> list = new ArrayList<>(qs.size());
        for (QaQuestion q : qs) {
            list.add(toAdminVO(q));
        }
        return list;
    }

    /** 新增/编辑题目：选项整图替换（已投票的题编辑选项会重置票数，V1 可接受） */
    @Transactional(rollbackFor = Exception.class)
    public void saveQuestion(Long id, QaQuestionSaveRequest req) {
        if (req == null || req.getContent() == null || req.getContent().isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "题目内容不能为空");
        }
        if (req.getCategoryId() == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "请选择分类");
        }
        if (req.getOptions() == null || req.getOptions().size() < 2) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "至少需要 2 个选项");
        }

        QaQuestion q = new QaQuestion();
        q.setCategoryId(req.getCategoryId());
        q.setContent(req.getContent());
        q.setDescription(req.getDescription());
        q.setStatus(req.getStatus() != null ? req.getStatus() : 1);
        q.setRecommendScore(req.getRecommendScore() != null ? req.getRecommendScore() : BigDecimal.ONE);
        q.setSortOrder(req.getSortOrder() != null ? req.getSortOrder() : 0);

        if (id == null) {
            q.setPublishedAt(LocalDateTime.now());
            questionMapper.insert(q);
        } else {
            q.setId(id);
            questionMapper.update(q);
            optionMapper.deleteByQuestionId(id);
        }

        List<QaQuestionOption> options = new ArrayList<>();
        int order = 1;
        for (QaQuestionSaveRequest.QaOptionSave os : req.getOptions()) {
            if (os.getContent() == null || os.getContent().isBlank()) {
                continue;
            }
            QaQuestionOption o = new QaQuestionOption();
            o.setQuestionId(q.getId());
            o.setContent(os.getContent());
            o.setIcon(os.getIcon());
            o.setSortOrder(os.getSortOrder() != null ? os.getSortOrder() : order);
            o.setVoteCount(0);
            options.add(o);
            order++;
        }
        if (!options.isEmpty()) {
            optionMapper.insertBatch(options);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteQuestion(Long id) {
        optionMapper.deleteByQuestionId(id);
        questionMapper.deleteById(id);
    }

    public void setQuestionStatus(Long id, Integer status) {
        questionMapper.updateStatus(id, status);
    }

    // ────────────── 内部 ──────────────

    private QaQuestionAdminVO toAdminVO(QaQuestion q) {
        QaQuestionAdminVO vo = new QaQuestionAdminVO();
        vo.setId(q.getId());
        vo.setCategoryId(q.getCategoryId());
        QaCategory c = categoryMapper.selectById(q.getCategoryId());
        vo.setCategoryName(c != null ? c.getName() : "");
        vo.setContent(q.getContent());
        vo.setDescription(q.getDescription());
        vo.setStatus(q.getStatus());
        vo.setAnswerCount(q.getAnswerCount());
        vo.setRecommendScore(q.getRecommendScore());
        vo.setSortOrder(q.getSortOrder());
        List<QaOptionAdminVO> opts = new ArrayList<>();
        for (QaQuestionOption o : optionMapper.selectByQuestionId(q.getId())) {
            QaOptionAdminVO ov = new QaOptionAdminVO();
            ov.setId(o.getId());
            ov.setContent(o.getContent());
            ov.setIcon(o.getIcon());
            ov.setSortOrder(o.getSortOrder());
            ov.setVoteCount(o.getVoteCount());
            opts.add(ov);
        }
        vo.setOptions(opts);
        return vo;
    }
}
