# 系统架构

## 模块划分

### 后端（Spring Boot）

```
common/     — 跨模块共享（ApiResponse, ErrorCode, BusinessException, GlobalExceptionHandler）
config/     — Spring 配置（PasswordEncoder, WebMvcConfig）
auth/       — 认证拦截（LoginInterceptor, @CurrentUser 注解 + 参数解析器）
user/       — 用户模块（controller, dto, domain, service, mapper）
web/        — 页面 Controller（转发到 Vue SPA）
```

### 前端（Vue 3 SPA）

```
webapp/
├── src/
│   ├── components/    — 7 个 UI 组件
│   ├── views/         — HomeView / LoginView / RegisterView
│   ├── router/        — Vue Router 路由
│   ├── api/           — API 接口层（当前为 Mock）
│   ├── mock/          — 热榜 / 社区 / 游戏 Mock 数据
│   ├── types/         — TypeScript 类型定义
│   ├── assets/svg/    — SVG 图标（logo / nav / common / platform / status）
│   └── assets/png/    — PNG 素材（hero 背景图）
├── vite.config.ts     — 构建输出 → src/main/resources/static/
└── package.json
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
页面请求（任意前端路由）
  → PageController → forward:/index.html
  → Vue Router 接管前端路由
  → 浏览器 JS fetch() 调用 /api/** 获取数据
  → JSON 渲染页面

API 请求（/api/**）
  → LoginInterceptor（检查 Session，排除 /api/auth/register、/api/auth/login、/api/users/**）
  → Controller（接收参数 + 调用 Service）
  → Service（业务逻辑）
  → Mapper → MyBatis XML（SQL）
  ← ApiResponse 统一返回
```

## 前端组件树

```
HomeView
├── Header           — 固定顶部，Logo + 导航 + 搜索 + 登录/注册
├── HeroBanner       — 活动区，背景图 + 标题 + 日期 + 摸鱼指数
├── Main Content (display:flex 两栏)
│   ├── HotRanking   — Tab 切换：微博热搜 / 百度热搜 / 知乎热榜
│   └── Sidebar (320px 固定宽)
│       ├── CommonHot          — 全网共同热点（跨平台热度对比）
│       ├── CommunityRecommend  — 社区帖子推荐（标题 + 评论数）
│       └── GameCard           — 小游戏入口（2×2 网格）
└── Footer
```

## 构建流程

```
npm install + npm run build（在 webapp/ 内执行）
  → 产物输出到 src/main/resources/static/
  → mvn package（将前端资源打入 JAR）
  → java -jar fishingtime.jar（Spring Boot 统一托管）
```

实际开发中：
- Maven 的 `generate-resources` 阶段通过 `exec-maven-plugin` 自动执行 `npm install + npm run build`
- IDEA 中点 Maven `package` 即可一次构建前后端

## Session 设计

Session 中仅保存：
- userId
- username
- nickname

禁止保存密码。

## 前后端分离方案

当前：Spring Boot（API 后端）+ Vue 3 SPA（前端独立项目 webapp/）

```
Spring Boot（纯 API 后端）  +  Vue 3（前端）
/api/**  ←→  fetch()
           HTML/CSS/JS 全由前端管理
```

关键点：
1. 前端项目独立在 `webapp/`，与后端代码隔离
2. API 统一返回 `ApiResponse` JSON
3. 前端通过 `fetch()` 调用 API，不依赖模板引擎
4. 构建产物输出到 `static/` 由 Spring Boot 托管
5. 拆分时只需将 `webapp/` 移到独立项目，后端添加 CORS 配置
