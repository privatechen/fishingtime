package com.fishingtime.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 页面 Controller — SPA 路由回退
 *
 * Vue 已构建到 src/main/resources/static/。
 * 所有前端路由（不含点号的路径）都转发到 Vue 的 index.html，
 * 由 Vue Router 处理前端路由。
 *
 * 解决：直接刷新 /games、/games/2048 等前端路由时的 404 问题
 */
@Controller
public class PageController {

    /**
     * SPA 回退：所有不含点号的路径转发到 index.html
     * - 排除含点号路径（.js/.css/.png 静态资源由 Spring 直接返回）
     * - /api/** 由 REST 控制器更具体的映射优先处理
     * - 覆盖 1~3 级前端路由（如 /games、/games/2048）
     */
    @GetMapping(value = {
            "/",
            "/{path:[^\\.]*}",
            "/{path:[^\\.]*}/{sub:[^\\.]*}",
            "/{path:[^\\.]*}/{sub:[^\\.]*}/{sub2:[^\\.]*}"
    })
    public String spaForward() {
        return "forward:/index.html";
    }
}
