package com.fishingtime.user.dto;

import lombok.Data;

/**
 * 用户信息 DTO — 返回前端，不含密码
 */
@Data
public class UserDTO {

    private Long id;
    private String username;
    private String nickname;
    private String email;
    private String avatarUrl;
    private Integer status;
}
