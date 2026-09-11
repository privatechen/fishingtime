package com.fishingtime.pricewatch.controller;

import com.fishingtime.auth.CurrentUser;
import com.fishingtime.auth.CurrentUserInfo;
import com.fishingtime.common.dto.ApiResponse;
import com.fishingtime.common.dto.ErrorCode;
import com.fishingtime.common.exception.BusinessException;
import com.fishingtime.pricewatch.dto.PriceHistoryPointResponse;
import com.fishingtime.pricewatch.dto.PriceWatchCreateRequest;
import com.fishingtime.pricewatch.dto.PriceWatchCreateResponse;
import com.fishingtime.pricewatch.dto.PriceWatchListItemResponse;
import com.fishingtime.pricewatch.dto.PriceguardPriceWatchCreateRequest;
import com.fishingtime.pricewatch.service.JdShortLinkResolver;
import com.fishingtime.pricewatch.service.PriceWatchService;
import com.fishingtime.pricewatch.service.TaobaoShortLinkResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/price-watch")
@RequiredArgsConstructor
public class PriceWatchController {
    private final PriceWatchService priceWatchService;
    private final TaobaoShortLinkResolver taobaoShortLinkResolver;
    private final JdShortLinkResolver jdShortLinkResolver;

    @GetMapping
    public ApiResponse<List<PriceWatchListItemResponse>> list(@CurrentUser CurrentUserInfo currentUser) {
        if (currentUser == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);
        return ApiResponse.success(priceWatchService.list(currentUser.getUserId()));
    }

    @GetMapping("/{watchId}/history")
    public ApiResponse<List<PriceHistoryPointResponse>> history(@CurrentUser CurrentUserInfo currentUser,
                                                                 @PathVariable Long watchId,
                                                                 @RequestParam(required = false, defaultValue = "30") Integer days) {
        if (currentUser == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);
        return ApiResponse.success(priceWatchService.history(currentUser.getUserId(), watchId, days));
    }

    @PostMapping
    public ApiResponse<PriceWatchCreateResponse> create(@CurrentUser CurrentUserInfo currentUser, @RequestBody PriceWatchCreateRequest request) {
        if (currentUser == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);
        return ApiResponse.success(priceWatchService.create(currentUser.getUserId(), request));
    }

    /**
     * Entry point for priceguard mini program.
     * It intentionally keeps the original share text and delegates to the same
     * create flow, so existing fishingtime behavior is unchanged.
     */
    @PostMapping("/priceguard")
    public ApiResponse<PriceWatchCreateResponse> createFromPriceguard(@CurrentUser CurrentUserInfo currentUser,
                                                                        @RequestBody PriceguardPriceWatchCreateRequest request) {
        if (currentUser == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);

        return ApiResponse.success(priceWatchService.createFromPriceguard(currentUser.getUserId(), request));
    }

    @PostMapping("/resolve-taobao-link")
    public ApiResponse<TaobaoShortLinkResolver.ResolveResult> resolveTaobaoLink(@RequestBody Map<String, String> request) {
        return ApiResponse.success(taobaoShortLinkResolver.resolve(request.get("input")));
    }

    @PostMapping("/resolve-jd-link")
    public ApiResponse<JdShortLinkResolver.ResolveResult> resolveJdLink(@RequestBody Map<String, String> request) {
        return ApiResponse.success(jdShortLinkResolver.resolve(request.get("input")));
    }

    @DeleteMapping("/{watchId}")
    public ApiResponse<Void> remove(@CurrentUser CurrentUserInfo currentUser, @PathVariable Long watchId) {
        if (currentUser == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);
        priceWatchService.remove(currentUser.getUserId(), watchId);
        return ApiResponse.success();
    }
}
