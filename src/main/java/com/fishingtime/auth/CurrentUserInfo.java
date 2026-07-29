package com.fishingtime.auth;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 当前登录用户信息 — Session 中保存的内容
 * 禁止保存密码或 BCrypt Hash
 */
@Data
@AllArgsConstructor
public class CurrentUserInfo {

    private Long userId;
    private String username;
    private String nickname;
}
