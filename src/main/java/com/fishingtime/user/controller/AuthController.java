package com.fishingtime.user.controller;

import com.fishingtime.auth.CurrentUser;
import com.fishingtime.auth.CurrentUserInfo;
import com.fishingtime.common.dto.ApiResponse;
import com.fishingtime.user.dto.LoginRequest;
import com.fishingtime.user.dto.RegisterRequest;
import com.fishingtime.user.dto.UserDTO;
import com.fishingtime.user.service.UserService;
import javax.servlet.http.HttpSession;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 认证 API
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    /**
     * 注册
     * POST /api/auth/register
     */
    @PostMapping("/register")
    public ApiResponse<UserDTO> register(@Valid @RequestBody RegisterRequest request) {
        UserDTO user = userService.register(request);
        return ApiResponse.success(user);
    }

    /**
     * 登录
     * POST /api/auth/login
     */
    @PostMapping("/login")
    public ApiResponse<UserDTO> login(@Valid @RequestBody LoginRequest request, HttpSession session) {
        CurrentUserInfo userInfo = userService.login(request);

        // Session 中仅保存 userId/username/nickname，禁止保存密码
        session.setAttribute("currentUser", userInfo);

        UserDTO dto = new UserDTO();
        dto.setId(userInfo.getUserId());
        dto.setUsername(userInfo.getUsername());
        dto.setNickname(userInfo.getNickname());

        return ApiResponse.success(dto);
    }

    /**
     * 退出
     * POST /api/auth/logout
     */
    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpSession session) {
        session.invalidate();
        return ApiResponse.success();
    }

    /**
     * 获取当前登录用户
     * GET /api/auth/current-user
     */
    @GetMapping("/current-user")
    public ApiResponse<UserDTO> currentUser(@CurrentUser CurrentUserInfo currentUser) {
        if (currentUser == null) {
            return ApiResponse.error(com.fishingtime.common.dto.ErrorCode.UNAUTHORIZED);
        }
        UserDTO dto = new UserDTO();
        dto.setId(currentUser.getUserId());
        dto.setUsername(currentUser.getUsername());
        dto.setNickname(currentUser.getNickname());
        return ApiResponse.success(dto);
    }
}
