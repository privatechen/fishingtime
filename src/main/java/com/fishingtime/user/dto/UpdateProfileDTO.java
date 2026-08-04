package com.fishingtime.user.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import lombok.Data;

/**
 * 更新个人资料请求
 */
@Data
public class UpdateProfileDTO {

    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 32, message = "用户名长度 3~32 个字符")
    private String username;

    @NotBlank(message = "昵称不能为空")
    @Size(max = 64, message = "昵称最长 64 个字符")
    private String nickname;

    @Size(max = 128, message = "邮箱最长 128 个字符")
    private String email;
}
