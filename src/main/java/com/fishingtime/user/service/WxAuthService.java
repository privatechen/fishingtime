package com.fishingtime.user.service;

import com.fishingtime.auth.CurrentUserInfo;
import com.fishingtime.auth.TokenService;
import com.fishingtime.common.dto.ErrorCode;
import com.fishingtime.common.exception.BusinessException;
import com.fishingtime.user.domain.User;
import com.fishingtime.user.dto.UserDTO;
import com.fishingtime.user.dto.WxLoginResult;
import com.fishingtime.user.mapper.UserMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * 微信小程序登录/注册服务
 *
 * 微信用户免密：认证完全依赖 OpenID（wx.login code → code2session）。
 * - login：识别已有用户，返回 token；首次（无用户）返回 needUsername，不建用户
 * - register：首次设置用户名建立用户（nickname = username，密码为空串不可用密码登录）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WxAuthService {

    private final UserMapper userMapper;
    private final TokenService tokenService;
    private final ObjectMapper objectMapper;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @Value("${wechat.appid}")
    private String appid;

    /** appsecret 通过环境变量注入（不写死在代码/版本库） */
    @Value("${wechat.secret}")
    private String secret;

    /** 微信登录：识别已有用户，首次返回 needUsername（不建用户，等前端设置用户名后走 register） */
    public WxLoginResult login(String code) {
        if (code == null || code.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "缺少微信登录 code");
        }

        String openid = code2Session(code);
        User user = userMapper.selectByOpenid(openid);
        if (user == null) {
            log.info("[微信] 首次识别 openid 前缀={}，等待设置用户名", maskOpenid(openid));
            return new WxLoginResult(true, null, null);
        }

        CurrentUserInfo info = new CurrentUserInfo(user.getId(), user.getUsername(), user.getNickname());
        String token = tokenService.createToken(info);
        return new WxLoginResult(false, token, toDTO(user));
    }

    /** 微信注册：首次设置用户名建立用户（免密，昵称与用户名保持一致） */
    public WxLoginResult register(String username, String code) {
        if (username == null || username.trim().length() < 3 || username.trim().length() > 32) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "用户名长度 3~32 个字符");
        }
        String name = username.trim();
        if (userMapper.selectByUsername(name) != null) {
            throw new BusinessException(ErrorCode.USERNAME_EXISTS);
        }

        String openid = code2Session(code);
        User user = userMapper.selectByOpenid(openid);
        if (user != null) {
            // 并发/重复注册：直接返回已有用户
            CurrentUserInfo info = new CurrentUserInfo(user.getId(), user.getUsername(), user.getNickname());
            return new WxLoginResult(false, tokenService.createToken(info), toDTO(user));
        }

        User newUser = new User();
        newUser.setUsername(name);
        newUser.setNickname(name); // 昵称与用户名保持一致
        newUser.setOpenid(openid);
        newUser.setStatus(1);
        newUser.setPassword(""); // 微信用户免密，密码不可用于登录，认证靠 OpenID
        userMapper.insertUser(newUser);
        log.info("[微信] 用户注册成功 userId={}, username={}", newUser.getId(), newUser.getUsername());

        CurrentUserInfo info = new CurrentUserInfo(newUser.getId(), newUser.getUsername(), newUser.getNickname());
        return new WxLoginResult(false, tokenService.createToken(info), toDTO(newUser));
    }

    /** 调微信 code2session 换取 OpenID */
    private String code2Session(String code) {
        String url = "https://api.weixin.qq.com/sns/jscode2session"
                + "?appid=" + appid
                + "&secret=" + secret
                + "&js_code=" + code
                + "&grant_type=authorization_code";
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            HttpResponse<String> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode root = objectMapper.readTree(resp.body());
            String openid = root.path("openid").asText(null);
            if (openid == null || openid.isEmpty()) {
                log.warn("[微信] code2session 失败，errcode={}", root.path("errcode").asText());
                throw new BusinessException(ErrorCode.LOGIN_FAILED, "微信登录凭证校验失败");
            }
            return openid;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("[微信] code2session 异常", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "微信登录服务异常");
        }
    }

    /** OpenID 脱敏，日志不记录完整值 */
    private String maskOpenid(String openid) {
        return openid.substring(0, Math.min(6, openid.length()));
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
