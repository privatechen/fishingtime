package com.fishingtime.auth;

import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 小程序 token 登录态（内存缓存）
 *
 * 小程序 wx.request 无法可靠携带 Web 的 Session cookie，故小程序登录返回 token，
 * 后续请求带 Authorization: Bearer &lt;token&gt;。
 * token 存内存：服务重启后失效，用户重新登录（V1 可接受）。
 */
@Component
public class TokenService {

    private final ConcurrentHashMap<String, CurrentUserInfo> tokens = new ConcurrentHashMap<>();

    public String createToken(CurrentUserInfo user) {
        String token = UUID.randomUUID().toString().replace("-", "");
        tokens.put(token, user);
        return token;
    }

    public CurrentUserInfo getUserByToken(String token) {
        return tokens.get(token);
    }

    public void removeToken(String token) {
        tokens.remove(token);
    }
}
