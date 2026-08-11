package com.fishingtime.user.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 微信小程序登录/注册结果
 */
@Data
@AllArgsConstructor
public class WxLoginResult {

    /** true = 首次使用，需前端引导设置用户名后再注册 */
    private Boolean needUsername;

    /** 已建立身份时的 token */
    private String token;

    /** 用户信息（已建立身份时返回） */
    private UserDTO user;
}
