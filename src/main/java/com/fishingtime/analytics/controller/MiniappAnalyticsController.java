package com.fishingtime.analytics.controller;

import com.fishingtime.analytics.dto.MiniappAnalyticsDTO;
import com.fishingtime.analytics.service.MiniappAnalyticsService;
import com.fishingtime.auth.CurrentUser;
import com.fishingtime.auth.CurrentUserInfo;
import com.fishingtime.common.dto.ApiResponse;
import com.fishingtime.common.dto.ErrorCode;
import com.fishingtime.game.service.DetailAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
public class MiniappAnalyticsController {

    private final MiniappAnalyticsService analyticsService;
    private final DetailAdminService detailAdminService;

    @PostMapping("/visit")
    public ApiResponse<Void> visit(@CurrentUser CurrentUserInfo currentUser) {
        if (currentUser == null) return ApiResponse.error(ErrorCode.UNAUTHORIZED);
        analyticsService.recordVisit(currentUser.getUserId());
        return ApiResponse.success();
    }

    @GetMapping("/admin/overview")
    public ApiResponse<MiniappAnalyticsDTO> overview(@CurrentUser CurrentUserInfo currentUser,
                                                     @RequestParam(defaultValue = "7") int days) {
        detailAdminService.requireAdmin(currentUser);
        return ApiResponse.success(analyticsService.overview(days));
    }
}
