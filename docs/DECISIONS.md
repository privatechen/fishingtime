# 架构决策记录

## 为什么采用 HttpSession 而非 JWT

- 当前为单体应用，无跨服务认证需求
- Session 天然具有服务端控制能力（可主动失效）
- 实现简单，无需额外依赖
- 未来如需前后端分离，可平滑添加 JWT 支持

## 为什么不用 JWT

第一阶段不需要。JWT 的痛点：
- 无法服务端主动失效（需黑名单）
- Token 刷新机制复杂

当前 HttpSession + 同源策略足够满足需求。

## 为什么不用 Spring Security

- Spring Security 的学习成本和配置复杂度远高于当前需求
- 当前只需要简单的 Session 登录拦截
- 自定义 LoginInterceptor 代码量少、可读性高、可控性强
- 未来如果权限模型复杂化，可引入 Spring Security 或 Shiro

## 为什么不用 Redis

第一阶段不需要。
- 当前无缓存需求
- 无分布式 Session 需求
- 无限流需求

## 为什么采用 MyBatis XML 而非注解 SQL

- SQL 与 Java 代码完全分离
- 便于 DBA review SQL
- 复杂查询更容易优化
- 统一管理 SQL 变更

## 为什么采用 Thymeleaf 而非前后端分离

- 第一阶段页面简单，Thymeleaf 开发效率高
- 已按前后端分离架构设计，API 与页面 Controller 分离
- JS 已通过 fetch 调用 API，不依赖模板引擎渲染数据
- 后续 Vue 拆分时，API 无需改动，只需迁移静态文件

## 为什么当前使用 Java 11 而非 Java 17

- 当前开发环境仅支持 Java 11
- 代码使用 Java 17 语法（var 等）兼容 11
- pom.xml 中 java.version 设为 11 以通过编译
- 目标版本为 Java 17，升级依赖版本后即可切换
- Spring Boot 2.7.18 可平滑升级至 3.x

## 为什么不在测试中使用 H2

- 不符合"不修改生产代码"原则
- 不需要为了测试而调整 SQL 方言
- Mock 测试速度更快、不依赖外部服务
- 完整覆盖了 Service + Controller + Interceptor 的逻辑
