package com.fishingtime.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * API 入参日志拦截器
 *
 * 打印每次接口请求的方法、路径、query 与请求参数（JSON 格式），便于联调与排查。
 */
@Component
public class ApiLogInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(ApiLogInterceptor.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }
        String query = request.getQueryString();
        String params = "{}";
        try {
            params = objectMapper.writeValueAsString(request.getParameterMap());
        } catch (Exception ignored) {
            // 参数序列化失败不影响请求
        }
        log.info("[API入参] {} {}?{} params={}",
                request.getMethod(), request.getRequestURI(),
                query == null ? "" : query, params);
        return true;
    }
}
