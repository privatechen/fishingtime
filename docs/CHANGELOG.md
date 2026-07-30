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

## 2026-07-30 — 前端 V0

### 新增
- Vue 3 + TypeScript + Vite 前端项目（webapp/）
- 首页完整页面：Header / HeroBanner / HotRanking / CommonHot / CommunityRecommend / GameCard / Footer
- 热榜 Tab 切换（微博 / 百度 / 知乎）Mock 数据
- 社区推荐、小游戏入口 Mock 数据
- 登录页、注册页（前端路由占位）
- 响应式布局，CSS Variables 设计系统
- SVG 图标素材（logo / 导航 / 通用 / 平台 / 状态 共 22 个）
- Hero 背景图 + 半透明遮罩层
- Maven 集成：exec-maven-plugin 自动执行 npm build
- PageController 改为 forward 到 Vue 的 index.html

### 修改
- PageController：Thymeleaf 渲染 → 转发到 Vue SPA
- application.yml / application-local.yml：数据库连接改为真实地址
- .gitignore：排除 node/、node_modules/、方案/ 目录

### 技术决策
- 采用 Vue 3 + TypeScript + Vite 替代 Thymeleaf
- 前端项目独立在 webapp/，构建产物输出到 src/main/resources/static/
- 通过 exec-maven-plugin 将 npm build 集成到 Maven 生命周期
- 使用 Mock 数据先行，后续替换为真实 API
- 组件化拆分，7 个独立 UI 组件
