# FishingTime 项目文档

## 项目目标

FishingTime 是一个娱乐性质的网站，面向钓鱼爱好者社区。项目将长期持续开发，未来可能增加发帖、评论、点赞、收藏、排行榜、游戏、活动、后台管理等功能。

## 当前阶段

第一阶段 — 用户模块

仅包含：
- 用户注册
- 用户登录
- 用户退出
- 获取当前登录用户
- 用户管理基础能力

## 当前完成情况

- [x] Spring Boot 项目初始化
- [x] 项目分层结构搭建
- [x] 统一响应 + 全局异常处理
- [x] Session 登录拦截器
- [x] 注册模块
- [x] 登录模块
- [x] 退出模块
- [x] 当前用户
- [x] 用户信息查询
- [x] 参数校验（Validation）
- [x] 登录页面
- [x] 注册页面
- [x] 首页
- [x] Mock 单元测试（20 个用例）
- [x] 文档体系（docs/）

## 下一阶段计划

待定（预计为用户中心或发帖模块）

## 技术栈

- Java 11（目标版本 Java 17）
- Spring Boot 2.7.18（可平滑升级至 3.x）
- Maven
- Spring MVC
- MyBatis + MyBatis XML
- MySQL
- Lombok
- Spring Validation
- BCryptPasswordEncoder
- Thymeleaf
- HttpSession
- Mockito + MockMvc

## 开发原则

- KISS / YAGNI / SOLID / DRY
- 不过度设计
- 页面与 API 彻底分离（便于后续 Vue 拆分）
- 文档与代码同步

## 当前限制

- 本机无 MySQL，SQL 已写好但不执行
- 当前使用 Java 11，目标为 Java 17
- 未引入 Redis/MQ/JWT/Docker/Vue 等
