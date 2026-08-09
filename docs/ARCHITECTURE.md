# 系统架构

> 最后同步：2026-08-05（对齐 git HEAD `0805-头条`；未包含未提交的碰撞游戏）

## 模块划分

### 后端（Spring Boot 2.7.18）

```
common/     — 跨模块共享（ApiResponse, ErrorCode, BusinessException, GlobalExceptionHandler）
config/     — Spring 配置（PasswordEncoderConfig, WebMvcConfig, SchedulingConfig）
auth/       — 认证拦截（LoginInterceptor, @CurrentUser 注解 + 参数解析器）
user/       — 用户模块（注册/登录/退出/资料查询/资料编辑）
hot/        — 热榜模块（5 平台抓取 + 内存缓存 + 定时刷新）
banner/     — 首页内容（每日一句 / 地区编码 / 天气）
game/       — 小游戏模块（2048 分数排行）
health/     — 健康检查（就绪探针）
web/        — SPA 回退 Controller（forward 到 Vue index.html）
```

### 前端（Vue 3 SPA）

```
webapp/
├── src/
│   ├── components/          — Header / HeroBanner / HotRanking / CommonHot / CommunityRecommend / GameCard / Footer
│   ├── views/               — HomeView / LoginView / RegisterView / ProfileView
│   ├── games/               — 游戏大厅 + 2048 + 鱼群碰撞（碰撞未提交）
│   │   ├── config/games.ts          — 游戏配置，驱动大厅渲染
│   │   ├── engine/                  — GameEngine 基类 + 2048 引擎
│   │   ├── components/hall/         — 游戏大厅（GameHall + GameCard）
│   │   ├── components/2048/         — 2048 全套组件
│   │   ├── stores/gameScore.ts      — 棋盘/分数 LocalStorage
│   │   └── collision/               — 鱼群碰撞（engine/render/stores/config）
│   ├── router/              — Vue Router 路由
│   ├── api/                 — auth.ts（真实 API）+ index.ts（热榜真实 API / 社区游戏 Mock）
│   ├── stores/auth.ts       — 全局登录态（响应式单例，不依赖 Pinia）
│   ├── mock/                — 热榜 / 社区 / 游戏 Mock 数据
│   ├── utils/weatherIcon.ts — 天气描述 → 图标映射（分昼夜）
│   ├── types/               — TypeScript 类型定义 + 平台配置 + 错误文案映射
│   ├── assets/              — svg（logo/nav/common/platform/status）+ png（hero/weather/评论/阅读量图标）
│   └── style.css            — CSS Variables 设计系统
├── vite.config.ts           — 构建输出 → static/；dev 代理 /api → localhost:8080
└── package.json             — Vue 3.5 + vue-router 4.5 + Vite 6 + TS
```

## 包结构

```
com.fishingtime
├── FishingTimeApplication.java
├── common/
│   ├── dto/
│   │   ├── ApiResponse.java
│   │   └── ErrorCode.java
│   └── exception/
│       ├── BusinessException.java
│       └── GlobalExceptionHandler.java
├── config/
│   ├── PasswordEncoderConfig.java
│   ├── WebMvcConfig.java
│   └── SchedulingConfig.java          # @EnableScheduling
├── auth/
│   ├── CurrentUser.java
│   ├── CurrentUserArgumentResolver.java
│   ├── CurrentUserInfo.java
│   └── LoginInterceptor.java
├── user/
│   ├── controller/                   # AuthController + UserController
│   ├── dto/                          # RegisterRequest / LoginRequest / UpdateProfileDTO / UserDTO
│   ├── domain/User.java
│   ├── service/ + impl/
│   └── mapper/ + UserMapper.xml
├── hot/
│   ├── controller/HotController.java
│   ├── crawler/                      # HotCrawler 接口 + 5 平台实现
│   ├── dto/HotItemDTO.java
│   ├── service/HotService.java       # 内存缓存 + 定时刷新
│   └── util/HotScoreParser.java      # 统一热度值归一化
├── banner/
│   ├── client/AmapClient.java        # 高德 IP 定位 + 实时天气
│   ├── controller/                   # DailySentence / Region / Weather
│   ├── dto/                          # DailySentenceDTO / RegionDTO / RegionImportDTO / WeatherDTO
│   ├── domain/                       # DailySentence / Region
│   ├── service/ + impl/
│   ├── mapper/ + XML
│   └── util/                         # AdcodeParser / IpUtils
├── game/
│   ├── controller/Game2048Controller.java
│   ├── domain/Game2048Score.java
│   ├── dto/                          # RankItemDTO / ScoreSubmitDTO
│   ├── service/ + impl/
│   └── mapper/ + Game2048ScoreMapper.xml
├── health/
│   ├── controller/HealthController.java
│   ├── dto/ReadinessResponse.java
│   └── service/ReadinessService.java
└── web/
    └── PageController.java           # SPA 回退
```

## 请求流程

```
页面请求（任意前端路由，最多 3 级）
  → PageController spaForward → forward:/index.html
  → Vue Router 接管前端路由
  → 浏览器 fetch() 调用 /api/** 获取数据
  → JSON 渲染页面

API 请求（/api/**）
  → LoginInterceptor（检查 Session，公开路径除外）
  → Controller（接收参数 + 调用 Service）
  → Service（业务逻辑）
  → Mapper → MyBatis XML（SQL）
  ← ApiResponse 统一返回（热榜 / 健康检查为例外，见 API.md）
```

SPA 回退规则（PageController）：不含点号的 1~3 级路径全部 forward 到 `index.html`；`.js/.css/.png` 等静态资源由 Spring 直接返回；`/api/**` 由更具体的 REST 映射优先处理。

## 热榜抓取架构

```
HotCrawler 接口（platform() + fetch()）
  ├── WeiboHotCrawler    — 先获取 Visitor Cookie（genvisitor2），再抓 HTML，解析 table
  ├── BaiduHotCrawler
  ├── ZhihuHotCrawler
  ├── HupuHotCrawler     — 回复/浏览/作者/时间，无热度值，按回复时间排序
  └── ToutiaoHotCrawler

HotService
  ├── @PostConstruct 启动即抓取一次
  ├── @Scheduled(fixedDelay=600_000) 每 10 分钟刷新
  ├── ConcurrentHashMap 内存缓存（platform → List<HotItemDTO>）
  ├── 记录 updateTime / nextRefreshTime，供前端判断缓存有效期
  └── 抓取失败保留旧缓存，不报错

新增平台：实现 HotCrawler 接口（@Component）即自动纳入缓存与定时刷新，前端加一个 Tab 即可。
```

## 天气链路

```
GET /api/weather
  → IpUtils 取真实 IP（含代理头处理）
  → AmapClient IP 定位 → adcode（失败回退默认城市 110000）
  → AmapClient 实时天气（extensions=base）
  → WeatherDTO{province, city, weather, temperature, humidity}

缓存（内存）：
  IP→adcode 24h；adcode→天气 10min
异常/定位失败 → 5001，前端隐藏天气模块，不影响首页其他内容
```

## 前端组件树

```
HomeView（/）
├── Header              — 固定顶部，Logo + 导航 + 登录态（useAuth）
├── HeroBanner          — 背景图 + 今日日期 + 今日一句(/api/daily-sentence) + 天气(/api/weather) + 摸鱼指数
├── Main Content（两栏）
│   ├── HotRanking      — 5 Tab（微博/百度/知乎/虎扑/头条）→ /api/hot/{platform}，前端按缓存有效期兜底
│   └── Sidebar
│       ├── CommonHot           — 全网共同热点
│       ├── CommunityRecommend  — 社区推荐（Mock）
│       └── GameCard            — 小游戏入口 → /games
└── Footer

GameHall（/games）       — 由 games.ts 配置驱动，登录用户显示各游戏个人最佳
├── hall/GameCard        — 可玩 / 敬请期待两态
└── 可玩：2048、鱼群碰撞；敬请期待：扫雷、五子棋、俄罗斯方块

Game2048（/games/2048）
├── GameBoard + Tile     — 棋盘渲染
├── ScorePanel           — 分数/最高分
├── RankingPanel         — 排行榜 → /api/games/2048/rank
├── RegisterDialog       — 提交分数 → /api/games/2048/score
├── RuleDialog / RestartDialog
└── 本地状态走 gameScoreStore（LocalStorage）

CollisionGame（/games/collision）— 鱼群碰撞，开发中（未提交）
ProfileView（/profile）  — 资料编辑 → /api/users/me（GET/PUT）
LoginView / RegisterView — 走 stores/auth.ts（真实 /api/auth/*）
```

## 构建流程

```
开发：
  webapp/ 下 npm run dev（Vite，端口 3000，/api 代理到 localhost:8080）
  后端 mvn spring-boot:run -Dspring.profiles.active=local

生产（一次构建前后端）：
  Maven generate-resources 阶段 exec-maven-plugin 自动执行
    npm --prefix webapp install
    npm --prefix webapp run build
  → 产物输出到 src/main/resources/static/
  → mvn package 将前端资源打入 JAR
  → java -jar fishingtime.jar（Spring Boot 统一托管）
```

## Session 设计

Session 中仅保存 `CurrentUserInfo`：
- userId
- username
- nickname

禁止保存密码。`PUT /api/users/me` 更新资料后会同步刷新 Session 中的用户名/昵称。

## 前后端分离方案

当前：Spring Boot（API 后端）+ Vue 3 SPA（前端独立项目 `webapp/`），**已完成分离**。

```
Spring Boot（纯 API 后端）  +  Vue 3（前端）
/api/**  ←→  fetch()
           HTML/CSS/JS 全由前端管理
```

关键点：
1. 前端项目独立在 `webapp/`，与后端代码隔离
2. API 统一返回 `ApiResponse` JSON（热榜 / 健康检查为例外）
3. 前端通过 `fetch()` 调用 API，不依赖模板引擎
4. 构建产物输出到 `static/` 由 Spring Boot 托管
5. 如需彻底拆分，仅需将 `webapp/` 移到独立项目并部署静态文件，后端添加 CORS 配置

遗留：`src/main/resources/templates/` 下的旧 Thymeleaf 页面（index/login/register.html）已废弃未清理。
