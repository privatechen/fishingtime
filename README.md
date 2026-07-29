# FishingTime

一个娱乐性质的钓鱼爱好者社区网站。当前处于第一阶段 —— 用户模块开发。

## 技术栈

| 技术 | 版本 |
|------|------|
| Java | 11（目标 17） |
| Spring Boot | 2.7.18（可升级至 3.x） |
| MyBatis | XML 全量手写 SQL |
| MySQL | 8.0+（SQL 已写好，当前未连接）|
| Thymeleaf | 页面模板 |
| HttpSession | 登录认证 |
| BCrypt | 密码加密 |
| Mockito + MockMvc | 单元测试 |

## 项目结构

```
fishingtime/
├── sql/init.sql                # 数据库建表 SQL
├── docs/                       # 8 份文档
├── src/main/java/com/fishingtime/
│   ├── common/                 # 统一响应 + 错误码 + 异常处理
│   ├── config/                 # 配置（BCrypt, WebMvc）
│   ├── auth/                   # Session 登录拦截器
│   ├── user/                   # 用户模块
│   │   ├── controller/         # AuthController + UserController
│   │   ├── dto/                # 请求/响应 DTO
│   │   ├── domain/             # User 实体
│   │   ├── service/            # UserService + impl
│   │   └── mapper/             # UserMapper + XML
│   └── web/                    # 页面 Controller
├── src/main/resources/
│   ├── templates/              # index.html, login.html, register.html
│   ├── static/css/             # 样式
│   └── static/js/              # 前端 JS（fetch 调用 API）
└── src/test/java/com/fishingtime/
    ├── user/                   # Service + Controller 测试
    └── web/                    # 拦截器测试
```

## 启动方式

```bash
# 当前阶段无需启动数据库

# 编译
mvn clean compile

# 运行测试（全 Mock，不连接数据库）
mvn test

# 如需本地运行，先创建数据库并执行 SQL
mysql -u root -p < sql/init.sql
# 然后使用 local profile 启动
mvn spring-boot:run -Dspring.profiles.active=local
```

## API

| 方法 | 路径 | 说明 | 需登录 |
|------|------|------|--------|
| POST | /api/auth/register | 注册 | 否 |
| POST | /api/auth/login | 登录 | 否 |
| POST | /api/auth/logout | 退出 | 是 |
| GET | /api/auth/current-user | 当前用户 | 是 |
| GET | /api/users/{id} | 用户信息 | 否 |

## 数据库

详见 [docs/DATABASE.md](docs/DATABASE.md)

## 测试

20 个测试用例覆盖：

- 注册成功 / 用户名重复 / 参数校验
- 登录成功 / 密码错误 / 账号禁用
- 退出成功
- 当前用户（已登录 / 未登录）
- 用户信息查询（存在 / 不存在）
- 登录拦截器（未登录 / 已登录 / OPTIONS）
- Controller 参数校验

## 后续前后端拆分方案

当前已按前后端分离架构设计：

1. 页面 Controller 只返回视图，不写业务
2. API 统一返回 JSON（ApiResponse）
3. 前端 JS 通过 fetch 调用 API
4. 拆分时只需将 templates/ + static/ 移到 Vue 项目
5. 后端添加 CORS 配置
