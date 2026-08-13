package com.fishingtime.config;

import com.fishingtime.auth.CurrentUserArgumentResolver;
import com.fishingtime.auth.LoginInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * Web MVC 配置
 * - 注册登录拦截器（仅拦截 /api/**，排除 /api/auth/**）
 * - 注册 @CurrentUser 参数解析器
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final LoginInterceptor loginInterceptor;
    private final CurrentUserArgumentResolver currentUserArgumentResolver;
    private final ApiLogInterceptor apiLogInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 入参日志优先打印（在认证拦截之前）
        registry.addInterceptor(apiLogInterceptor)
                .addPathPatterns("/api/**");
        registry.addInterceptor(loginInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/auth/register", "/api/auth/login", "/api/auth/wx-login", "/api/auth/wx-register", "/api/users/**", "/api/hot/**", "/api/region/**", "/api/weather/**", "/api/health/**", "/api/daily-sentence/**", "/api/feedback", "/api/games/2048/rank", "/api/games/color-focus/rank", "/api/games/direction-trap/rank", "/api/games/color-hunter/rank", "/api/games/fish-breakout/rank");
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(currentUserArgumentResolver);
    }
}
