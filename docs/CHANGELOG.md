# 变更日志

## 2026-07-29 — 项目初始化

### 新增
- Spring Boot 项目初始化，完整分层结构
- 用户模块：注册、登录、退出、当前用户、用户信息查询
- Session 登录拦截器 + @CurrentUser 注解
- 统一响应 ApiResponse + 全局异常处理
- 参数校验（Validation）
- 注册页面、登录页面、首页
- MyBatis XML 全量手写 SQL
- Mock 单元测试（20 个用例覆盖 Service + Controller + Interceptor）
- 完整 docs 文档体系（8 份文档）

### 技术决策
- 采用 HttpSession 而非 JWT
- 采用 BCryptPasswordEncoder 加密密码
- 采用 MyBatis XML 而非注解 SQL
- 页面与 API 彻底分离，为后续 Vue 拆分做准备
- 仅在 application-test.yml 中排除数据源，测试全 Mock
