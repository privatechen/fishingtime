package com.fishingtime.user.dto;

import lombok.Data;

/**
 * 微信小程序登录请求
 */
@Data
public class WxLoginRequest {

    /** wx.login 获取的临时 code */
    private String code;

    /** 来源小程序 appid（后端按此选对应 secret） */
    private String appId;
}
