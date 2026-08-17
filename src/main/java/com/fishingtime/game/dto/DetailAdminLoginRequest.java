package com.fishingtime.game.dto;

import lombok.Data;

/**
 * 《细节》管理后台登录请求
 */
@Data
public class DetailAdminLoginRequest {

    private String username;
    private String password;
}
