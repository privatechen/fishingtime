package com.fishingtime.user;

import com.fishingtime.auth.CurrentUserInfo;
import com.fishingtime.common.dto.ErrorCode;
import com.fishingtime.common.exception.BusinessException;
import com.fishingtime.user.domain.User;
import com.fishingtime.user.dto.LoginRequest;
import com.fishingtime.user.dto.RegisterRequest;
import com.fishingtime.user.dto.UserDTO;
import com.fishingtime.user.mapper.UserMapper;
import com.fishingtime.user.service.impl.UserServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private BCryptPasswordEncoder passwordEncoder;

    @InjectMocks
    private UserServiceImpl userService;

    @Test
    @DisplayName("注册成功")
    void registerSuccess() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("testuser");
        request.setPassword("123456");
        request.setNickname("Test");
        request.setEmail("test@example.com");

        when(userMapper.selectByUsername("testuser")).thenReturn(null);
        when(passwordEncoder.encode("123456")).thenReturn("$2a$10$encoded_hash");
        doAnswer(invocation -> {
            User u = invocation.getArgument(0);
            u.setId(100L);
            return null;
        }).when(userMapper).insertUser(any());

        UserDTO result = userService.register(request);

        assertNotNull(result);
        assertEquals(100L, result.getId());
        assertEquals("testuser", result.getUsername());
        assertEquals("Test", result.getNickname());
        verify(userMapper).insertUser(any());
    }

    @Test
    @DisplayName("注册时用户名已存在")
    void registerDuplicateUsername() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("existing");
        request.setPassword("123456");
        request.setNickname("Test");

        when(userMapper.selectByUsername("existing")).thenReturn(new User());

        assertThrows(BusinessException.class, () -> userService.register(request),
                () -> ErrorCode.USERNAME_EXISTS.getMessage());
        verify(userMapper, never()).insertUser(any());
    }

    @Test
    @DisplayName("登录成功")
    void loginSuccess() {
        LoginRequest request = new LoginRequest();
        request.setUsername("testuser");
        request.setPassword("123456");

        User user = new User();
        user.setId(100L);
        user.setUsername("testuser");
        user.setNickname("Test");
        user.setPassword("$2a$10$encoded_hash");
        user.setStatus(1);

        when(userMapper.selectByUsername("testuser")).thenReturn(user);
        when(passwordEncoder.matches("123456", "$2a$10$encoded_hash")).thenReturn(true);

        CurrentUserInfo result = userService.login(request);

        assertNotNull(result);
        assertEquals(100L, result.getUserId());
        assertEquals("testuser", result.getUsername());
        assertEquals("Test", result.getNickname());
    }

    @Test
    @DisplayName("登录时密码错误")
    void loginWrongPassword() {
        LoginRequest request = new LoginRequest();
        request.setUsername("testuser");
        request.setPassword("wrong");

        User user = new User();
        user.setUsername("testuser");
        user.setPassword("$2a$10$encoded_hash");
        user.setStatus(1);

        when(userMapper.selectByUsername("testuser")).thenReturn(user);
        when(passwordEncoder.matches("wrong", "$2a$10$encoded_hash")).thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class, () -> userService.login(request));
        assertEquals(ErrorCode.LOGIN_FAILED.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("登录时用户被禁用")
    void loginDisabledUser() {
        LoginRequest request = new LoginRequest();
        request.setUsername("disabled");
        request.setPassword("123456");

        User user = new User();
        user.setUsername("disabled");
        user.setPassword("hash");
        user.setStatus(0);

        when(userMapper.selectByUsername("disabled")).thenReturn(user);

        BusinessException ex = assertThrows(BusinessException.class, () -> userService.login(request));
        assertEquals(ErrorCode.USER_DISABLED.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("根据ID获取用户")
    void getUserById() {
        User user = new User();
        user.setId(100L);
        user.setUsername("testuser");
        user.setNickname("Test");
        user.setEmail("test@example.com");
        user.setStatus(1);
        user.setCreatedAt(LocalDateTime.now());

        when(userMapper.selectById(100L)).thenReturn(user);

        UserDTO result = userService.getUserById(100L);

        assertNotNull(result);
        assertEquals(100L, result.getId());
        assertEquals("testuser", result.getUsername());
        assertEquals("test@example.com", result.getEmail());
    }

    @Test
    @DisplayName("获取不存在的用户")
    void getUserByIdNotFound() {
        when(userMapper.selectById(999L)).thenReturn(null);

        assertThrows(BusinessException.class, () -> userService.getUserById(999L));
    }
}
