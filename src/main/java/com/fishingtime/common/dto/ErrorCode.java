package com.fishingtime.common.dto;

import lombok.Getter;

/**
 * 错误码定义
 */
@Getter
public enum ErrorCode {

    SUCCESS(200, "success"),
    PARAM_INVALID(400, "请求参数校验失败"),
    UNAUTHORIZED(401, "未登录"),
    FORBIDDEN(403, "无权限"),
    NOT_FOUND(404, "资源不存在"),

    USERNAME_EXISTS(1001, "用户名已存在"),
    USER_NOT_FOUND(1002, "用户不存在"),
    LOGIN_FAILED(1003, "用户名或密码错误"),
    USER_DISABLED(1004, "账号已被禁用"),
    PASSWORD_INVALID(1005, "密码格式错误"),

    SYSTEM_ERROR(5000, "系统异常"),
    ;

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
