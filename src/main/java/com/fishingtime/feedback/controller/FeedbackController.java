package com.fishingtime.feedback.controller;

import com.fishingtime.auth.CurrentUser;
import com.fishingtime.auth.CurrentUserInfo;
import com.fishingtime.common.dto.ApiResponse;
import com.fishingtime.common.dto.ErrorCode;
import com.fishingtime.common.exception.BusinessException;
import com.fishingtime.feedback.dto.FeedbackRequest;
import com.fishingtime.feedback.service.FeedbackService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户反馈 API
 *
 * POST /api/feedback — 提交反馈（公开；登录则记录 userId，游客也可提交）
 */
@Slf4j
@RestController
@RequestMapping("/api/feedback")
@RequiredArgsConstructor
public class FeedbackController {

    private static final int MAX_CONTENT_LENGTH = 500;

    private final FeedbackService feedbackService;

    @PostMapping
    public ApiResponse<Void> submit(@CurrentUser CurrentUserInfo currentUser,
                                    @RequestBody FeedbackRequest request) {
        if (request == null || request.getContent() == null || request.getContent().trim().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "反馈内容不能为空");
        }
        String content = request.getContent().trim();
        if (content.length() > MAX_CONTENT_LENGTH) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "反馈内容不能超过 500 字");
        }
        Long userId = currentUser != null ? currentUser.getUserId() : null;
        feedbackService.submit(userId, content);
        return ApiResponse.success();
    }
}
