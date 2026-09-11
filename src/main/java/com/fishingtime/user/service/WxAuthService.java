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
 * 微信小程序/小游戏登录服务。
 *
 * 同一套后端可以服务多个微信应用，但用户身份按 appId + openid 隔离。
 * 历史用户如果 wx_appid 为空，会在首次登录时自动绑定当前 appId，避免重复建号。
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

    /** 微信登录：按 appId + openid 查找用户；首次登录静默创建游客账号。 */
    public WxLoginResult login(String code, String appId) {
        if (code == null || code.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "缺少微信登录 code");
        }

        String openid = code2Session(code, appId);
        User user = findOrAdoptUser(openid, appId);
        if (user == null) {
            user = createGuestUser(openid, appId);
        }

        CurrentUserInfo info = new CurrentUserInfo(user.getId(), user.getUsername(), user.getNickname());
        String token = tokenService.createToken(info);
        return new WxLoginResult(false, token, toDTO(user));
    }

    /**
     * 先按 appId + openid 查找；若没有，再兼容一次历史数据（wx_appid 为空），并绑定到当前 appId。
     */
    private User findOrAdoptUser(String openid, String appId) {
        User user = userMapper.selectByOpenidAndAppId(openid, appId);
        if (user != null) {
            return user;
        }

        User legacy = userMapper.selectLegacyByOpenid(openid);
        if (legacy == null) {
            return null;
        }

        int updated = userMapper.bindWxAppid(legacy.getId(), appId);
        if (updated > 0) {
            legacy.setWxAppid(appId);
            log.info("[微信] 历史用户绑定 appId, userId={}, appId={}", legacy.getId(), appId);
            return legacy;
        }

        return userMapper.selectByOpenidAndAppId(openid, appId);
    }

    /** 静默创建游客账号。 */
    private User createGuestUser(String openid, String appId) {
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
        user.setWxAppid(appId);
        user.setStatus(1);
        user.setPassword("");
        userMapper.insertUser(user);
        log.info("[微信] 静默创建游客用户 userId={}, nickname={}, appId={}", user.getId(), nickname, appId);
        return user;
    }

    /** 微信注册：首次设置用户名建立用户。 */
    public WxLoginResult register(String username, String code, String appId) {
        if (username == null || username.trim().length() < 3 || username.trim().length() > 32) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "用户名长度 3~32 个字符");
        }
        String name = username.trim();
        if (userMapper.selectByUsername(name) != null) {
            throw new BusinessException(ErrorCode.USERNAME_EXISTS);
        }

        String openid = code2Session(code, appId);
        User user = findOrAdoptUser(openid, appId);
        if (user != null) {
            CurrentUserInfo info = new CurrentUserInfo(user.getId(), user.getUsername(), user.getNickname());
            return new WxLoginResult(false, tokenService.createToken(info), toDTO(user));
        }

        User newUser = new User();
        newUser.setUsername(name);
        newUser.setNickname(name);
        newUser.setOpenid(openid);
        newUser.setWxAppid(appId);
        newUser.setStatus(1);
        newUser.setPassword("");
        userMapper.insertUser(newUser);
        log.info("[微信] 用户注册成功 userId={}, username={}, appId={}", newUser.getId(), newUser.getUsername(), appId);

        CurrentUserInfo info = new CurrentUserInfo(newUser.getId(), newUser.getUsername(), newUser.getNickname());
        return new WxLoginResult(false, tokenService.createToken(info), toDTO(newUser));
    }

    /** 调微信 code2session 换取 OpenID。 */
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

        String safeUrl = "https://api.weixin.qq.com/sns/jscode2session"
                + "?appid=" + appId
                + "&secret=" + maskSecret(appSecret)
                + "&js_code=" + maskCode(code)
                + "&grant_type=authorization_code";
        log.info("[微信] code2session 请求 url={}, appId={}, secretLength={}", safeUrl, appId, appSecret.length());

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
                log.warn("[微信] code2session 失败，appId={}, httpStatus={}, errcode={}, errmsg={}",
                        appId,
                        resp.statusCode(),
                        root.path("errcode").asText(),
                        root.path("errmsg").asText());
                throw new BusinessException(ErrorCode.LOGIN_FAILED, "微信登录凭证校验失败");
            }
            return openid;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("[微信] code2session 异常, appId={}", appId, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "微信登录服务异常");
        }
    }

    private String maskSecret(String secret) {
        if (secret == null || secret.isEmpty()) {
            return "<empty>";
        }
        if (secret.length() <= 6) {
            return "***";
        }
        return secret.substring(0, 3) + "***" + secret.substring(secret.length() - 3);
    }

    private String maskCode(String code) {
        if (code == null || code.isEmpty()) {
            return "<empty>";
        }
        if (code.length() <= 8) {
            return "***";
        }
        return code.substring(0, 4) + "***" + code.substring(code.length() - 4);
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
