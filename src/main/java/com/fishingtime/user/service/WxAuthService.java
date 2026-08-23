package com.fishingtime.user.service;

import com.fishingtime.auth.CurrentUserInfo;
import com.fishingtime.auth.TokenService;
import com.fishingtime.common.dto.ErrorCode;
import com.fishingtime.common.exception.BusinessException;
import com.fishingtime.config.WechatProperties;
import com.fishingtime.user.domain.User;
import com.fishingtime.user.dto.UserDTO;
import com.fishingtime.user.dto.WxLoginResult;
import com.fishingtime.user.mapper.UserMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
 * 支持多个小程序共用一套后端：客户端上报 appId，code2session 用对应 appid 的 secret。
 * - login：识别已有用户；首次（新 openid）静默创建游客账号（昵称「人民xxxxx」递增）
 * - register：设置用户名建立用户（免密，昵称=用户名）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WxAuthService {

    private final UserMapper userMapper;
    private final TokenService tokenService;
    private final ObjectMapper objectMapper;
    private final WechatProperties wechatProperties;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    /** 微信登录：识别已有用户；首次（新 openid）静默创建游客账号（昵称「人民xxxxx」递增），全程无弹窗 */
    public WxLoginResult login(String code, String appId) {
        if (code == null || code.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "缺少微信登录 code");
        }

        String openid = code2Session(code, appId);
        User user = userMapper.selectByOpenid(openid);
        if (user == null) {
            user = createGuestUser(openid);
        }

        CurrentUserInfo info = new CurrentUserInfo(user.getId(), user.getUsername(), user.getNickname());
        String token = tokenService.createToken(info);
        return new WxLoginResult(false, token, toDTO(user));
    }

    /** 静默创建游客账号：用户名 wx+openid（唯一），昵称「人民00001」按序号递增；免密登录（认证靠 OpenID） */
    private User createGuestUser(String openid) {
        String username = "wx" + openid;
        if (username.length() > 32) {
            username = username.substring(0, 32);
        }
        long guestNo = userMapper.selectMaxGuestNo() + 1;
        String nickname = "人民" + String.format("%05d", guestNo);

        User user = new User();
        user.setUsername(username);
        user.setNickname(nickname);
        user.setOpenid(openid);
        user.setStatus(1);
        user.setPassword("");
        userMapper.insertUser(user);
        log.info("[微信] 静默创建游客用户 userId={}, nickname={}", user.getId(), nickname);
        return user;
    }

    /** 微信注册：首次设置用户名建立用户（免密，昵称与用户名保持一致） */
    public WxLoginResult register(String username, String code, String appId) {
        if (username == null || username.trim().length() < 3 || username.trim().length() > 32) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "用户名长度 3~32 个字符");
        }
        String name = username.trim();
        if (userMapper.selectByUsername(name) != null) {
            throw new BusinessException(ErrorCode.USERNAME_EXISTS);
        }

        String openid = code2Session(code, appId);
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
    private String code2Session(String code, String appId) {
        if (appId == null || appId.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "缺少 appId");
        }
        String appSecret = wechatProperties.secretFor(appId);
        if (appSecret == null || appSecret.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "未配置该小程序的 secret");
        }
        String url = "https://api.weixin.qq.com/sns/jscode2session"
                + "?appid=" + appId
                + "&secret=" + appSecret
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
