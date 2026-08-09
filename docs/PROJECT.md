# FishingTime 项目文档

> 最后同步：2026-08-05（git HEAD `0805-头条`，含未提交的碰撞游戏）

## 项目目标

FishingTime 是一个娱乐性质的网站，面向钓鱼爱好者社区。项目长期持续开发，已落地：用户体系、热榜聚合、每日一句、天气、小游戏。规划中：发帖、评论、点赞、收藏、排行榜、后台管理等。

## 开发阶段（按 git 提交划分）

| 阶段 | 时间 | 内容 |
|------|------|------|
| 一 · 用户模块 | 07-29 | Spring Boot 初始化、注册/登录/退出/当前用户、Session 拦截、20 个 Mock 测试 |
| 二 · 前端 V0 | 07-30 | Vue 3 + TS + Vite 前端、首页组件树、登录/注册页、Maven 集成 npm build |
| 三 · 热榜聚合 | 07-30~07-31 | 热榜抓取（微博/百度/知乎→虎扑）、统一热度值、内存缓存 + 定时刷新、readiness 探针 |
| 四 · 内容与游戏 | 08-02~08-05 | 每日一句、天气、地区编码、2048 游戏、游戏大厅、头条热榜、碰撞游戏（开发中） |

## 当前完成情况

- [x] Spring Boot 项目初始化 + 分层结构
- [x] 统一响应 + 全局异常处理
- [x] Session 登录拦截器 + @CurrentUser
- [x] 用户：注册 / 登录 / 退出 / 当前用户 / 用户信息查询
- [x] 用户：资料查询与编辑（/api/users/me）
- [x] 热榜：微博 / 百度 / 知乎 / 虎扑 / 头条 5 平台抓取
- [x] 热榜：统一热度值归一化 + 内存缓存 + 10min 定时刷新
- [x] 每日一句：内存加载、每日固定、跨天切换
- [x] 地区：高德 adcode 编码表 + 批量导入 + 子级查询
- [x] 天气：IP 定位 → 高德实时天气，双层缓存（IP 24h / 天气 10min）
- [x] 2048 游戏：排行榜 / 我的最高分 / 分数提交
- [x] 游戏大厅：配置驱动，2048 / 鱼群碰撞可玩，扫雷/五子棋/俄罗斯方块敬请期待
- [x] 健康检查：readiness 就绪探针（app + database）
- [x] 前端：首页 / 登录 / 注册 / 个人中心 / 游戏大厅 / 2048
- [x] 测试：41 个用例（用户模块 + 拦截器 + 热榜热度解析 21）
- [x] 文档体系（docs/ 8 份）

## 进行中

- [ ] 鱼群碰撞游戏（webapp/src/games/collision/，**未提交**）
- [ ] 新表 DDL 补全（见 DATABASE.md 缺口）

## 技术栈

### 后端

| 技术 | 版本/说明 |
|------|-----------|
| Java | 11（目标 17） |
| Spring Boot | 2.7.18（可平滑升级 3.x） |
| Spring MVC | REST + @Scheduled 定时任务 |
| MyBatis | XML 全量手写 SQL |
| MySQL | 8，远端 123.207.213.29:3306/fishingtime |
| HttpSession | 登录认证（非 JWT） |
| BCrypt | 密码加密（spring-security-crypto） |
| Jsoup | 热榜 HTML 抓取解析 |
| java.net.http.HttpClient | 高德天气 / IP 定位、微博 Cookie 获取 |
| Lombok / Spring Validation | |
| Mockito + MockMvc | 单元测试（全 Mock，不连库） |

### 前端

| 技术 | 版本/说明 |
|------|-----------|
| Vue | 3.5（Composition API + `<script setup>`） |
| TypeScript | ~5.7 |
| Vite | 6 |
| Vue Router | 4.5 |
| 状态管理 | 自定义响应式单例（stores/auth.ts），未用 Pinia |
| UI | 无框架，CSS Variables 设计系统 |

### 构建

- Maven `generate-resources` 阶段 exec-maven-plugin 自动执行 `npm install + npm run build`
- 前端产物输出到 `src/main/resources/static/`，由 Spring Boot 统一托管
- 开发时前端 `npm run dev`（端口 3000，`/api` 代理到 8080）

## 开发原则

- KISS / YAGNI / SOLID / DRY
- 不过度设计；页面与 API 彻底分离
- 文档与代码同步
- Mock 数据先行，逐步替换为真实 API

## 当前限制 / 待办

- **DB DDL 缺口**：`region_code`、`daily_sentence`、`game_2048_score` 三表仓库无建表脚本，仅远端手工建
- **README.md 滞后**：仍停留在第一阶段（用户模块），未同步热榜/游戏/天气等内容
- **遗留文件**：`templates/` 下旧 Thymeleaf 页面（index/login/register.html）已废弃未清理
- **前端 Mock 残留**：社区推荐、全网共同热点、游戏列表仍为 Mock 数据
- **碰撞游戏**：开发中未提交（含 static 产物、router/games.ts 改动）
- **外部依赖脆弱**：热榜抓取依赖目标站点 HTML 结构（微博依赖 Visitor Cookie 机制），结构变更可能失效；高德接口受 Key 配额限制
- **凭据明文**：MySQL / 高德 Key 明文存于 application.yml，仅限学习环境
- 热榜数据为服务端内存缓存，应用重启后需重新抓取（启动即抓取一次）

## 下一阶段计划

- 发帖 / 评论 / 点赞模块
- 用户中心完善（头像上传等）
- 游戏扩展（扫雷 / 五子棋 / 俄罗斯方块）
- 热榜稳定性与平台扩展
- 前端 Mock 全部替换为真实 API
