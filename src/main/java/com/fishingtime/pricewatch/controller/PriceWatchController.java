package com.fishingtime.pricewatch.controller;

import com.fishingtime.auth.CurrentUser;
import com.fishingtime.auth.CurrentUserInfo;
import com.fishingtime.common.dto.ApiResponse;
import com.fishingtime.common.dto.ErrorCode;
import com.fishingtime.common.exception.BusinessException;
import com.fishingtime.pricewatch.dto.PriceWatchCreateRequest;
import com.fishingtime.pricewatch.dto.PriceWatchCreateResponse;
import com.fishingtime.pricewatch.dto.PriceWatchListItemResponse;
import com.fishingtime.pricewatch.service.PriceWatchService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/price-watch")
@RequiredArgsConstructor
public class PriceWatchController {

    private final PriceWatchService priceWatchService;

    @GetMapping
    public ApiResponse<List<PriceWatchListItemResponse>> list(@CurrentUser CurrentUserInfo currentUser) {
        if (currentUser == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return ApiResponse.success(priceWatchService.list(currentUser.getUserId()));
    }

    @PostMapping
    public ApiResponse<PriceWatchCreateResponse> create(@CurrentUser CurrentUserInfo currentUser,
                                                         @RequestBody PriceWatchCreateRequest request) {
        if (currentUser == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return ApiResponse.success(priceWatchService.create(currentUser.getUserId(), request));
    }
}
