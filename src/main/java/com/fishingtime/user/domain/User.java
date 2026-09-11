package com.fishingtime.user.domain;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户实体
 */
@Data
public class User {

    private Long id;
    private String username;
    private String password;
    private String nickname;
    private String email;
    /** 微信应用内的 OpenID */
    private String openid;
    /** OpenID 所属的微信应用 AppID，用于区分小游戏、小程序、PriceGuard 等业务用户 */
    private String wxAppid;
    private String avatarUrl;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
