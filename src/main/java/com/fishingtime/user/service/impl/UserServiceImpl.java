package com.fishingtime.user.service.impl;

import com.fishingtime.auth.CurrentUserInfo;
import com.fishingtime.common.dto.ErrorCode;
import com.fishingtime.common.exception.BusinessException;
import com.fishingtime.user.domain.User;
import com.fishingtime.user.dto.LoginRequest;
import com.fishingtime.user.dto.RegisterRequest;
import com.fishingtime.user.dto.UpdateProfileDTO;
import com.fishingtime.user.dto.UserDTO;
import com.fishingtime.user.mapper.UserMapper;
import com.fishingtime.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 用户服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final BCryptPasswordEncoder passwordEncoder;

    @Override
    public UserDTO register(RegisterRequest request) {
        // 检查用户名是否已存在
        User existing = userMapper.selectByUsername(request.getUsername());
        if (existing != null) {
            throw new BusinessException(ErrorCode.USERNAME_EXISTS);
        }

        // 创建用户
        User user = new User();
        user.setUsername(request.getUsername());
        // BCrypt 哈希密码，禁止明文存储或记录日志
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setNickname(request.getNickname());
        user.setEmail(request.getEmail());
        user.setStatus(1); // 正常

        userMapper.insertUser(user);

        log.info("用户注册成功: userId={}, username={}", user.getId(), user.getUsername());

        return toDTO(user);
    }

    @Override
    public CurrentUserInfo login(LoginRequest request) {
        User user = userMapper.selectByUsername(request.getUsername());
        if (user == null) {
            throw new BusinessException(ErrorCode.LOGIN_FAILED);
        }

        // 检查账号状态
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BusinessException(ErrorCode.USER_DISABLED);
        }

        // 验证密码
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.LOGIN_FAILED);
        }

        log.info("用户登录成功: userId={}, username={}", user.getId(), user.getUsername());

        return new CurrentUserInfo(user.getId(), user.getUsername(), user.getNickname());
    }

    @Override
    public UserDTO getUserById(Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        return toDTO(user);
    }

    @Override
    public UserDTO getProfile(Long userId) {
        return getUserById(userId);
    }

    @Override
    public UserDTO updateProfile(Long userId, UpdateProfileDTO dto) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        // 检查用户名是否与其他用户冲突（排除自己）
        User existing = userMapper.selectByUsername(dto.getUsername());
        if (existing != null && !existing.getId().equals(userId)) {
            throw new BusinessException(ErrorCode.USERNAME_EXISTS);
        }

        user.setUsername(dto.getUsername());
        user.setNickname(dto.getNickname());
        user.setEmail(dto.getEmail());
        userMapper.updateProfile(user);

        log.info("用户更新资料: userId={}", userId);
        return toDTO(user);
    }

    private UserDTO toDTO(User user) {
        UserDTO dto = new UserDTO();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setNickname(user.getNickname());
        dto.setEmail(user.getEmail());
        dto.setAvatarUrl(user.getAvatarUrl());
        dto.setStatus(user.getStatus());
        return dto;
    }
}
