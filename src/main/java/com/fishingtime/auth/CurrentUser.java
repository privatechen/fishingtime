package com.fishingtime.auth;

import java.lang.annotation.*;

/**
 * 标注在 Controller 方法参数上，自动注入当前登录用户信息
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CurrentUser {
}
