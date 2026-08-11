package com.fishingtime.user.dto;

import lombok.Data;

/**
 * 微信小程序登录请求
 */
@Data
public class WxLoginRequest {

    /** wx.login 获取的临时 code */
    private String code;
}
