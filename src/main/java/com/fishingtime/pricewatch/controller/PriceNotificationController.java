package com.fishingtime.pricewatch.controller;

import com.fishingtime.auth.CurrentUser;
import com.fishingtime.auth.CurrentUserInfo;
import com.fishingtime.common.dto.ApiResponse;
import com.fishingtime.common.dto.ErrorCode;
import com.fishingtime.common.exception.BusinessException;
import com.fishingtime.pricewatch.dto.PriceNotificationResponse;
import com.fishingtime.pricewatch.service.PriceNotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/price-notifications")
@RequiredArgsConstructor
public class PriceNotificationController {

    private final PriceNotificationService notificationService;

    @GetMapping("/unread")
    public ApiResponse<List<PriceNotificationResponse>> unread(@CurrentUser CurrentUserInfo currentUser) {
        if (currentUser == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);
        return ApiResponse.success(notificationService.unread(currentUser.getUserId()));
    }

    @PutMapping("/{notificationId}/read")
    public ApiResponse<Void> markRead(@CurrentUser CurrentUserInfo currentUser,
                                      @PathVariable Long notificationId) {
        if (currentUser == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);
        notificationService.markRead(currentUser.getUserId(), notificationId);
        return ApiResponse.success();
    }
}
