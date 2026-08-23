package com.fishingtime.qa.controller;

import com.fishingtime.auth.CurrentUser;
import com.fishingtime.auth.CurrentUserInfo;
import com.fishingtime.common.dto.ApiResponse;
import com.fishingtime.common.dto.ErrorCode;
import com.fishingtime.qa.dto.QaAnswerPage;
import com.fishingtime.qa.dto.QaAnswerRequest;
import com.fishingtime.qa.dto.QaCategoryVO;
import com.fishingtime.qa.dto.QaNextResponse;
import com.fishingtime.qa.dto.QaProfileStatsVO;
import com.fishingtime.qa.dto.QaQuestionVO;
import com.fishingtime.qa.dto.QaSubmitRequest;
import com.fishingtime.qa.service.QaService;
import com.fishingtime.qa.service.QaSubmitService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 「瞅瞅」问答 API（需登录，/api/qa/** 走登录拦截器）
 *
 * GET  /api/qa/categories                    — 分类
 * GET  /api/qa/questions/next?categoryCode=  — 下一道未答题（recommend=推荐策略）
 * GET  /api/qa/questions/{id}               — 题目详情（已答返回统计）
 * POST /api/qa/questions/{id}/answer        — 提交答案（事务+幂等，返回最新统计）
 * GET  /api/qa/answers                      — 我的回答历史
 */
@Slf4j
@RestController
@RequestMapping("/api/qa")
@RequiredArgsConstructor
public class QaController {

    private final QaService qaService;
    private final QaSubmitService qaSubmitService;

    @GetMapping("/categories")
    public ApiResponse<List<QaCategoryVO>> categories() {
        return ApiResponse.success(qaService.categories());
    }

    @GetMapping("/questions/next")
    public ApiResponse<QaNextResponse> next(@CurrentUser CurrentUserInfo user,
                                            @RequestParam(defaultValue = "recommend") String categoryCode) {
        return ApiResponse.success(qaService.next(user.getUserId(), categoryCode));
    }

    @GetMapping("/questions/{id}")
    public ApiResponse<QaQuestionVO> detail(@CurrentUser CurrentUserInfo user, @PathVariable Long id) {
        return ApiResponse.success(qaService.detail(user.getUserId(), id));
    }

    @PostMapping("/questions/{id}/answer")
    public ApiResponse<QaQuestionVO> answer(@CurrentUser CurrentUserInfo user,
                                            @PathVariable Long id,
                                            @RequestBody QaAnswerRequest request) {
        if (request == null || request.getOptionId() == null) {
            return ApiResponse.error(ErrorCode.PARAM_INVALID);
        }
        return ApiResponse.success(qaService.answer(user.getUserId(), id, request.getOptionId()));
    }

    @GetMapping("/profile/stats")
    public ApiResponse<QaProfileStatsVO> profileStats(@CurrentUser CurrentUserInfo user) {
        return ApiResponse.success(qaService.profileStats(user.getUserId()));
    }

    @PostMapping("/submit")
    public ApiResponse<Void> submit(@CurrentUser CurrentUserInfo user,
                                    @RequestBody QaSubmitRequest request) {
        if (request == null) {
            return ApiResponse.error(ErrorCode.PARAM_INVALID);
        }
        qaSubmitService.submit(user.getUserId(), request);
        return ApiResponse.success();
    }

    @GetMapping("/answers")
    public ApiResponse<QaAnswerPage> answers(@CurrentUser CurrentUserInfo user,
                                             @RequestParam(defaultValue = "1") int page,
                                             @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.success(qaService.history(user.getUserId(), page, pageSize));
    }
}
