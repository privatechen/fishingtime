package com.fishingtime.user.controller;

import com.fishingtime.auth.CurrentUser;
import com.fishingtime.auth.CurrentUserInfo;
import com.fishingtime.common.dto.ApiResponse;
import com.fishingtime.user.dto.UpdateProfileDTO;
import com.fishingtime.user.dto.UserDTO;
import com.fishingtime.user.service.UserService;
import javax.servlet.http.HttpSession;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 用户信息 API
 */
@Slf4j
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * 获取用户信息（公开）
     * GET /api/users/{id}
     */
    @GetMapping("/{id}")
    public ApiResponse<UserDTO> getUser(@PathVariable Long id) {
        UserDTO user = userService.getUserById(id);
        return ApiResponse.success(user);
    }

    /**
     * 获取当前用户完整资料（需登录）
     * GET /api/users/me
     */
    @GetMapping("/me")
    public ApiResponse<UserDTO> getProfile(@CurrentUser CurrentUserInfo currentUser) {
        if (currentUser == null) {
            return ApiResponse.error(com.fishingtime.common.dto.ErrorCode.UNAUTHORIZED);
        }
        return ApiResponse.success(userService.getProfile(currentUser.getUserId()));
    }

    /**
     * 更新当前用户资料（需登录）
     * PUT /api/users/me
     */
    @PutMapping("/me")
    public ApiResponse<UserDTO> updateProfile(@CurrentUser CurrentUserInfo currentUser,
                                              @Valid @RequestBody UpdateProfileDTO dto,
                                              HttpSession session) {
        if (currentUser == null) {
            return ApiResponse.error(com.fishingtime.common.dto.ErrorCode.UNAUTHORIZED);
        }
        UserDTO updated = userService.updateProfile(currentUser.getUserId(), dto);

        // 同步 Session 中的用户名/昵称，保证 Header 立即更新
        session.setAttribute("currentUser",
                new CurrentUserInfo(updated.getId(), updated.getUsername(), updated.getNickname()));

        return ApiResponse.success(updated);
    }
}
