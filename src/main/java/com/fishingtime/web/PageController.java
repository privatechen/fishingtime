package com.fishingtime.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 页面 Controller — 转发所有前端路由到 Vue 构建产物
 *
 * Vue 已构建到 src/main/resources/static/，
 * 不再使用 Thymeleaf 模板引擎。
 *
 * 所有非 API 路径都返回 Vue 的 index.html，
 * 由 Vue Router 处理前端路由。
 */
@Controller
public class PageController {

    /**
     * 首页
     */
    @GetMapping("/")
    public String index() {
        return "forward:/index.html";
    }

    /**
     * 登录页（由 Vue Router 处理）
     */
    @GetMapping("/login")
    public String login() {
        return "forward:/index.html";
    }

    /**
     * 注册页（由 Vue Router 处理）
     */
    @GetMapping("/register")
    public String register() {
        return "forward:/index.html";
    }
}
