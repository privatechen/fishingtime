package com.fishingtime.qa.controller;

import com.fishingtime.auth.CurrentUser;
import com.fishingtime.auth.CurrentUserInfo;
import com.fishingtime.common.dto.ApiResponse;
import com.fishingtime.game.service.DetailAdminService;
import com.fishingtime.qa.dto.QaCategoryAdminVO;
import com.fishingtime.qa.dto.QaCategorySaveRequest;
import com.fishingtime.qa.dto.QaQuestionAdminVO;
import com.fishingtime.qa.dto.QaQuestionSaveRequest;
import com.fishingtime.qa.dto.QaSubmitAdminVO;
import com.fishingtime.qa.dto.QaSubmitReviewRequest;
import com.fishingtime.qa.service.QaAdminService;
import com.fishingtime.qa.service.QaSubmitService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 「瞅瞅」管理后台 API（需 admin 身份：当前登录用户 == admin-user）
 *
 * GET    /api/qa/admin/categories            — 分类列表（含题数）
 * POST   /api/qa/admin/categories            — 新增分类
 * PUT    /api/qa/admin/categories            — 更新分类
 * DELETE /api/qa/admin/categories/{id}       — 删除分类（有题则拒绝）
 * GET    /api/qa/admin/questions?categoryId= — 题目列表（含选项）
 * POST   /api/qa/admin/questions             — 新增题目
 * PUT    /api/qa/admin/questions/{id}        — 更新题目
 * DELETE /api/qa/admin/questions/{id}        — 删除题目
 * PATCH  /api/qa/admin/questions/{id}/status — 上下线
 */
@Slf4j
@RestController
@RequestMapping("/api/qa/admin")
@RequiredArgsConstructor
public class QaAdminController {

    private final QaAdminService qaAdminService;
    private final QaSubmitService qaSubmitService;
    private final DetailAdminService detailAdminService;

    private void requireAdmin(CurrentUserInfo user) {
        detailAdminService.requireAdmin(user);
    }

    @GetMapping("/categories")
    public ApiResponse<List<QaCategoryAdminVO>> listCategories(@CurrentUser CurrentUserInfo user) {
        requireAdmin(user);
        return ApiResponse.success(qaAdminService.listCategories());
    }

    @PostMapping("/categories")
    public ApiResponse<Void> createCategory(@CurrentUser CurrentUserInfo user,
                                            @RequestBody QaCategorySaveRequest req) {
        requireAdmin(user);
        qaAdminService.saveCategory(req);
        return ApiResponse.success();
    }

    @PutMapping("/categories")
    public ApiResponse<Void> updateCategory(@CurrentUser CurrentUserInfo user,
                                            @RequestBody QaCategorySaveRequest req) {
        requireAdmin(user);
        if (req == null || req.getId() == null) {
            return ApiResponse.error(com.fishingtime.common.dto.ErrorCode.PARAM_INVALID);
        }
        qaAdminService.saveCategory(req);
        return ApiResponse.success();
    }

    @DeleteMapping("/categories/{id}")
    public ApiResponse<Void> deleteCategory(@CurrentUser CurrentUserInfo user, @PathVariable Long id) {
        requireAdmin(user);
        qaAdminService.deleteCategory(id);
        return ApiResponse.success();
    }

    @GetMapping("/questions")
    public ApiResponse<List<QaQuestionAdminVO>> listQuestions(@CurrentUser CurrentUserInfo user,
                                                              @RequestParam(required = false) Long categoryId) {
        requireAdmin(user);
        return ApiResponse.success(qaAdminService.listQuestions(categoryId));
    }

    @PostMapping("/questions")
    public ApiResponse<Void> createQuestion(@CurrentUser CurrentUserInfo user,
                                            @RequestBody QaQuestionSaveRequest req) {
        requireAdmin(user);
        qaAdminService.saveQuestion(null, req);
        return ApiResponse.success();
    }

    @PutMapping("/questions/{id}")
    public ApiResponse<Void> updateQuestion(@CurrentUser CurrentUserInfo user,
                                            @PathVariable Long id,
                                            @RequestBody QaQuestionSaveRequest req) {
        requireAdmin(user);
        qaAdminService.saveQuestion(id, req);
        return ApiResponse.success();
    }

    @DeleteMapping("/questions/{id}")
    public ApiResponse<Void> deleteQuestion(@CurrentUser CurrentUserInfo user, @PathVariable Long id) {
        requireAdmin(user);
        qaAdminService.deleteQuestion(id);
        return ApiResponse.success();
    }

    @PatchMapping("/questions/{id}/status")
    public ApiResponse<Void> setStatus(@CurrentUser CurrentUserInfo user,
                                       @PathVariable Long id,
                                       @RequestParam Integer status) {
        requireAdmin(user);
        qaAdminService.setQuestionStatus(id, status);
        return ApiResponse.success();
    }

    // ────────────── 投稿审核 ──────────────

    @GetMapping("/submissions")
    public ApiResponse<List<QaSubmitAdminVO>> listSubmissions(@CurrentUser CurrentUserInfo user,
                                                              @RequestParam(required = false) Integer status) {
        requireAdmin(user);
        return ApiResponse.success(qaSubmitService.list(status));
    }

    @PostMapping("/submissions/{id}/approve")
    public ApiResponse<Void> approve(@CurrentUser CurrentUserInfo user, @PathVariable Long id) {
        requireAdmin(user);
        qaSubmitService.approve(id);
        return ApiResponse.success();
    }

    @PostMapping("/submissions/{id}/reject")
    public ApiResponse<Void> reject(@CurrentUser CurrentUserInfo user,
                                    @PathVariable Long id,
                                    @RequestBody(required = false) QaSubmitReviewRequest request) {
        requireAdmin(user);
        qaSubmitService.reject(id, request != null ? request.getReason() : null);
        return ApiResponse.success();
    }
}
