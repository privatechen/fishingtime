package com.fishingtime.pricewatch.controller;

import com.fishingtime.auth.CurrentUser;
import com.fishingtime.auth.CurrentUserInfo;
import com.fishingtime.common.dto.ApiResponse;
import com.fishingtime.common.dto.ErrorCode;
import com.fishingtime.common.exception.BusinessException;
import com.fishingtime.pricewatch.dto.PriceWatchCreateRequest;
import com.fishingtime.pricewatch.dto.PriceWatchCreateResponse;
import com.fishingtime.pricewatch.dto.PriceWatchRecognizeRequest;
import com.fishingtime.pricewatch.dto.PriceWatchRecognizeResponse;
import com.fishingtime.pricewatch.service.PriceWatchService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/price-watch")
@RequiredArgsConstructor
public class PriceWatchController {

    private final PriceWatchService priceWatchService;

    @PostMapping("/recognize")
    public ApiResponse<PriceWatchRecognizeResponse> recognize(@CurrentUser CurrentUserInfo currentUser,
                                                               @RequestBody PriceWatchRecognizeRequest request) {
        if (currentUser == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "请求参数不能为空");
        }
        return ApiResponse.success(priceWatchService.recognize(request.getProductUrl()));
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
