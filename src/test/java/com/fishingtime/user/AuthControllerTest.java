package com.fishingtime.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fishingtime.auth.CurrentUserInfo;
import com.fishingtime.user.dto.LoginRequest;
import com.fishingtime.user.dto.RegisterRequest;
import com.fishingtime.user.dto.UserDTO;
import com.fishingtime.user.mapper.UserMapper;
import com.fishingtime.user.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserService userService;

    @MockBean
    private com.fishingtime.banner.mapper.RegionMapper regionMapper;

    @MockBean
    private javax.sql.DataSource dataSource;

    @MockBean
    private com.fishingtime.banner.mapper.DailySentenceMapper dailySentenceMapper;

    @MockBean
    private com.fishingtime.game.mapper.Game2048ScoreMapper game2048ScoreMapper;

    /**
     * Mock UserMapper 以通过 MyBatis 加载
     * 注：application-test.yml 已排除 DataSource/MyBatis 自动配置，
     * 此 MockBean 确保 Service 层能正常注入
     */
    @MockBean
    private UserMapper userMapper;

    @Test
    @DisplayName("注册 — 成功")
    void registerSuccess() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("newuser");
        request.setPassword("123456");
        request.setNickname("NewUser");

        UserDTO dto = new UserDTO();
        dto.setId(1L);
        dto.setUsername("newuser");
        dto.setNickname("NewUser");
        dto.setStatus(1);

        when(userService.register(any())).thenReturn(dto);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.username").value("newuser"));
    }

    @Test
    @DisplayName("注册 — 参数校验失败（用户名空）")
    void registerValidationFail() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("");
        request.setPassword("123456");
        request.setNickname("Test");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("登录 — 成功")
    void loginSuccess() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setUsername("testuser");
        request.setPassword("123456");

        CurrentUserInfo userInfo = new CurrentUserInfo(1L, "testuser", "TestUser");
        when(userService.login(any())).thenReturn(userInfo);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.username").value("testuser"));
    }

    @Test
    @DisplayName("退出 — 成功（需登录）")
    void logoutSuccess() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("currentUser", new CurrentUserInfo(1L, "testuser", "TestUser"));
        mockMvc.perform(post("/api/auth/logout")
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("获取当前用户 — 未登录（被拦截器拦截）")
    void currentUserUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/auth/current-user"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("获取当前用户 — 已登录")
    void currentUserAuthenticated() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("currentUser", new CurrentUserInfo(1L, "testuser", "TestUser"));

        mockMvc.perform(get("/api/auth/current-user")
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.username").value("testuser"));
    }

    @Test
    @DisplayName("登录 — 参数校验失败")
    void loginValidationFail() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setUsername("");
        request.setPassword("");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }
}
