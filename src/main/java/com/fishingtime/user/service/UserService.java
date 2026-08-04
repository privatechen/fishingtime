package com.fishingtime.user.service;

import com.fishingtime.auth.CurrentUserInfo;
import com.fishingtime.user.dto.LoginRequest;
import com.fishingtime.user.dto.RegisterRequest;
import com.fishingtime.user.dto.UpdateProfileDTO;
import com.fishingtime.user.dto.UserDTO;

/**
 * 用户服务接口
 */
public interface UserService {

    /**
     * 用户注册
     * @return 注册成功的用户信息
     */
    UserDTO register(RegisterRequest request);

    /**
     * 用户登录
     * @return 登录成功后的用户会话信息
     */
    CurrentUserInfo login(LoginRequest request);

    /**
     * 根据 ID 获取用户信息
     */
    UserDTO getUserById(Long id);

    /**
     * 获取当前用户完整资料（含邮箱等）
     */
    UserDTO getProfile(Long userId);

    /**
     * 更新个人资料
     * @return 更新后的用户信息
     */
    UserDTO updateProfile(Long userId, UpdateProfileDTO dto);
}
