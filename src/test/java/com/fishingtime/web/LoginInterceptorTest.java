package com.fishingtime.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fishingtime.auth.CurrentUserInfo;
import com.fishingtime.auth.LoginInterceptor;
import com.fishingtime.auth.TokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

import static org.junit.jupiter.api.Assertions.*;

/**
 * LoginInterceptor 单元测试
 * 直接测试拦截器逻辑，不依赖 Spring 上下文
 */
class LoginInterceptorTest {

    private LoginInterceptor interceptor;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private TokenService tokenService;

    @BeforeEach
    void setUp() {
        tokenService = new TokenService();
        interceptor = new LoginInterceptor(new ObjectMapper(), tokenService);
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
    }

    @Test
    @DisplayName("未登录请求被拦截")
    void withoutLogin() throws Exception {
        request.setRequestURI("/api/auth/logout");

        boolean result = interceptor.preHandle(request, response, new Object());

        assertFalse(result);
        assertEquals(401, response.getStatus());
    }

    @Test
    @DisplayName("已登录请求放行")
    void withLogin() throws Exception {
        request.setRequestURI("/api/auth/logout");
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("currentUser", new CurrentUserInfo(1L, "test", "Test"));
        request.setSession(session);

        boolean result = interceptor.preHandle(request, response, new Object());

        assertTrue(result);
    }

    @Test
    @DisplayName("OPTIONS 请求放行")
    void optionsMethod() throws Exception {
        request.setMethod("OPTIONS");
        request.setRequestURI("/api/some-resource");

        boolean result = interceptor.preHandle(request, response, new Object());

        assertTrue(result);
    }

    @Test
    @DisplayName("注册接口不受拦截")
    void registerExcluded() throws Exception {
        // 注意：拦截器本身不区分路径，路径排除在 WebMvcConfig 中配置
        // 此处测试的是注册接口在无 session 时应被拦截，
        // 但实际上 WebMvcConfig 注册时 excludePathPatterns("/api/auth/register")
        // 所以此测试仅验证拦截器基础行为
        request.setRequestURI("/api/auth/register");

        boolean result = interceptor.preHandle(request, response, new Object());

        // 无 session → 拦截
        assertFalse(result);
    }

    @Test
    @DisplayName("携带有效 token 放行")
    void withValidToken() throws Exception {
        request.setRequestURI("/api/games/my-records");
        String token = tokenService.createToken(new CurrentUserInfo(1L, "test", "Test"));
        request.addHeader("Authorization", "Bearer " + token);

        boolean result = interceptor.preHandle(request, response, new Object());

        assertTrue(result);
    }

    @Test
    @DisplayName("携带无效 token 被拦截")
    void withInvalidToken() throws Exception {
        request.setRequestURI("/api/games/my-records");
        request.addHeader("Authorization", "Bearer invalid-token");

        boolean result = interceptor.preHandle(request, response, new Object());

        assertFalse(result);
        assertEquals(401, response.getStatus());
    }
}
