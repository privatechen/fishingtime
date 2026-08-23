package com.fishingtime.user.dto;

import lombok.Data;

/**
 * 微信小程序首次注册请求（免密，只需设置用户名）
 */
@Data
public class WxRegisterRequest {

    /** wx.login 获取的临时 code */
    private String code;

    /** 用户名（唯一，昵称与之保持一致） */
    private String username;

    /** 来源小程序 appid（后端按此选对应 secret） */
    private String appId;
}
