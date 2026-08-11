package com.fishingtime.user.controller;

import com.fishingtime.common.dto.ApiResponse;
import com.fishingtime.user.dto.WxLoginRequest;
import com.fishingtime.user.dto.WxLoginResult;
import com.fishingtime.user.dto.WxRegisterRequest;
import com.fishingtime.user.service.WxAuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 微信小程序认证 API
 *
 * POST /api/auth/wx-login     — 识别已有用户；首次返回 needUsername
 * POST /api/auth/wx-register  — 首次设置用户名建立用户（免密，昵称=用户名）
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class WxAuthController {

    private final WxAuthService wxAuthService;

    @PostMapping("/wx-login")
    public ApiResponse<WxLoginResult> wxLogin(@RequestBody WxLoginRequest request) {
        return ApiResponse.success(wxAuthService.login(request.getCode()));
    }

    @PostMapping("/wx-register")
    public ApiResponse<WxLoginResult> wxRegister(@RequestBody WxRegisterRequest request) {
        return ApiResponse.success(wxAuthService.register(request.getUsername(), request.getCode()));
    }
}
