# 系统架构

## 模块划分

```
common/     — 跨模块共享（ApiResponse, ErrorCode, BusinessException, GlobalExceptionHandler）
config/     — Spring 配置（PasswordEncoder, WebMvcConfig）
auth/       — 认证拦截（LoginInterceptor, @CurrentUser 注解 + 参数解析器）
user/       — 用户模块（controller, dto, domain, service, mapper）
web/        — 页面 Controller（返回 Thymeleaf 视图）
```

## 包结构

```
com.fishingtime
├── FishingTimeApplication.java
├── common/
│   ├── dto/
│   │   ├── ApiResponse.java
│   │   └── ErrorCode.java
│   ├── exception/
│   │   ├── BusinessException.java
│   │   └── GlobalExceptionHandler.java
├── config/
│   ├── PasswordEncoderConfig.java
│   └── WebMvcConfig.java
├── auth/
│   ├── CurrentUser.java
│   ├── CurrentUserArgumentResolver.java
│   ├── CurrentUserInfo.java
│   └── LoginInterceptor.java
├── user/
│   ├── controller/
│   │   ├── AuthController.java
│   │   └── UserController.java
│   ├── dto/
│   │   ├── RegisterRequest.java
│   │   ├── LoginRequest.java
│   │   └── UserDTO.java
│   ├── domain/
│   │   └── User.java
│   ├── service/
│   │   ├── UserService.java
│   │   └── impl/UserServiceImpl.java
│   └── mapper/
│       ├── UserMapper.java
│       └── UserMapper.xml
└── web/
    └── PageController.java
```

## 请求流程

```
页面请求（/login, /register, /）→ PageController → 返回 Thymeleaf 视图
  → 浏览器 JS 调用 /api/** 获取数据 → JSON 渲染页面

API 请求（/api/**）
  → LoginInterceptor（检查 Session）
  → Controller（接收参数 + 调用 Service）
  → Service（业务逻辑）
  → Mapper → MyBatis XML（SQL）
  ← ApiResponse 统一返回
```

## Session 设计

Session 中仅保存：
- userId
- username
- nickname

禁止保存密码。

## 前后端拆分方案

当前：Spring Boot + Thymeleaf（页面由后端渲染）

未来拆分：
```
Spring Boot（纯 API 后端）  +  Vue（前端）
                                      
/api/**  ←→  axios/fetch
           HTML/CSS/JS 全由前端管理
```

拆分关键点：
1. 页面 Controller 与 API Controller 已分离
2. API 已统一返回 `ApiResponse` JSON
3. 前端 JS 已通过 `fetch` 调用 API
4. 拆分时只需将 templates/static 移到 Vue 项目
5. 后端添加 CORS 配置即可
