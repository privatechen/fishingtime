# API 文档

> 最后同步：2026-08-05（对齐 git HEAD `0805-头条`；未包含未提交的碰撞游戏改动）

## 统一返回格式

### 标准格式（ApiResponse）

```json
{
  "code": 200,
  "message": "success",
  "data": {},
  "timestamp": 1690700000
}
```

`timestamp` 为 epoch 秒。`data` 为 null 的字段会被省略（`@JsonInclude(NON_NULL)`）。

### 例外 1：热榜接口（GET /api/hot/{platform}）

非标准 ApiResponse，结构为 `{ code, message, updateTime, nextRefreshTime, data }`：

```json
{
  "code": 200,
  "message": "success",
  "updateTime": "2026-08-05T10:30:00",
  "nextRefreshTime": "2026-08-05T10:40:00",
  "data": []
}
```

- `updateTime` / `nextRefreshTime`：本次及下次刷新时间，供前端判断缓存有效期
- `data` 为空时 `code=404`、`message=暂无 {platform} 热榜数据`

### 例外 2：健康检查（GET /api/health/readiness）

返回 `ReadinessResponse`，**状态以 HTTP 状态码表达**（非 ApiResponse.code）：

```json
{
  "status": "READY",
  "checks": { "app": "UP", "database": "UP" },
  "timestamp": 1690700000
}
```

- 就绪 → HTTP 200（status=READY）
- 未就绪（如数据库不可用）→ HTTP 503（status=NOT_READY）

## 错误码

| code | message | 备注 |
|------|---------|------|
| 200 | success | |
| 400 | 请求参数校验失败 | |
| 401 | 未登录 | |
| 403 | 无权限 | |
| 404 | 资源不存在 | 热榜接口亦用 404 表示无数据 |
| 1001 | 用户名已存在 | |
| 1002 | 用户不存在 | |
| 1003 | 用户名或密码错误 | |
| 1004 | 账号已被禁用 | |
| 1005 | 密码格式错误 | 已定义但暂未在代码中使用 |
| 5000 | 系统异常 | |
| 5001 | 天气信息暂时不可用 | WeatherController 硬编码，未纳入 ErrorCode 枚举 |

---

## 接口列表

### 认证（/api/auth）

#### POST /api/auth/register — 注册（公开）

Request：
```json
{
  "username": "fisher",          // 必填，3~32 字符
  "password": "123456",          // 必填，6~64 字符
  "nickname": "钓鱼人",           // 必填，最长 64
  "email": "fisher@example.com"  // 可选，最长 128
}
```

Response（完整 UserDTO）：
```json
{
  "code": 200,
  "data": { "id": 1, "username": "fisher", "nickname": "钓鱼人", "email": "fisher@example.com", "avatarUrl": null, "status": 1 }
}
```

用户名重复 → 1001。

#### POST /api/auth/login — 登录（公开）

Request：
```json
{ "username": "fisher", "password": "123456" }
```

Response：
```json
{
  "code": 200,
  "data": { "id": 1, "username": "fisher", "nickname": "钓鱼人" }
}
```

登录成功后将 `CurrentUserInfo(userId, username, nickname)` 写入 Session（key: `currentUser`）。

#### POST /api/auth/logout — 退出（需登录）

Response：
```json
{ "code": 200, "data": null }
```

直接 `session.invalidate()`。

#### GET /api/auth/current-user — 获取当前登录用户（需登录）

Response：
```json
{
  "code": 200,
  "data": { "id": 1, "username": "fisher", "nickname": "钓鱼人" }
}
```

未登录时返回 401。

### 用户（/api/users）

#### GET /api/users/{id} — 获取用户信息（公开）

Response：
```json
{
  "code": 200,
  "data": { "id": 1, "username": "fisher", "nickname": "钓鱼人", "email": null, "avatarUrl": null, "status": 1 }
}
```

用户不存在 → 1002。

#### GET /api/users/me — 获取当前用户完整资料（需登录）

Response：同 `GET /api/users/{id}`，返回完整 UserDTO。

#### PUT /api/users/me — 更新当前用户资料（需登录）

Request：
```json
{
  "username": "new_fisher",  // 必填，3~32
  "nickname": "新昵称",       // 必填，最长 64
  "email": "new@example.com" // 可选，最长 128
}
```

Response：更新后的完整 UserDTO。

- 用户名与他人冲突（排除自己）→ 1001
- 成功后同步更新 Session 中的 username/nickname，Header 立即生效

### 热榜（/api/hot）

#### GET /api/hot/{platform} — 获取平台热榜（公开）

`platform` ∈ `weibo` | `baidu` | `zhihu` | `hupu` | `toutiao`

返回自定义结构（见「例外 1」）。`data` 元素（HotItemDTO）：
```json
{
  "rank": 1,
  "title": "热榜标题",
  "hotScore": "1200000",
  "url": "https://...",
  "summary": "爆",
  "normalizedHotScore": 95
}
```

- 微博/百度/知乎/头条有统一热度值 `normalizedHotScore`（0~100，按该值降序展示）
- 虎扑无热度值，附加字段：`replyCount`（回复数）、`viewCount`（浏览数）、`author`（作者）、`publishTime`（发布时间），按回复时间排序
- 数据由后端定时抓取缓存（启动 + 每 10 分钟），`updateTime`/`nextRefreshTime` 供前端判断缓存有效期

### 每日一句（/api/daily-sentence）

#### GET /api/daily-sentence/random — 随机返回一条（公开）

Response：
```json
{
  "code": 200,
  "data": { "content": "今天也要开心一点～", "category": null }
}
```

实际为「今日一句」：同一自然日固定返回同一条，跨天自动切换；数据库为空时返回默认文案。

### 地区（/api/region）

高德行政区划编码表（表 `region_code`）。

#### GET /api/region/{adcode} — 查询单条（公开）

Response：
```json
{
  "code": 200,
  "data": { "name": "北京市", "adcode": "110000", "citycode": "010", "level": 1, "parentAdcode": null }
}
```

#### GET /api/region — 分页查询（公开）

Query：`page`（默认 1）、`size`（默认 10，最大 100）、`name`（可选，模糊匹配）

#### GET /api/region/children/{adcode} — 查询子级（公开）

返回该 adcode 的所有直接子级。

#### POST /api/region/import — 批量导入（公开）

Request：`RegionImportDTO` 数组，`{ name, adcode, citycode }`。
`level` / `parentAdcode` 由后端按 adcode 推断，每 500 条一批插入。返回导入条数。

### 天气（/api/weather）

#### GET /api/weather — 获取当前用户地区实时天气（公开）

Response：
```json
{
  "code": 200,
  "data": { "province": "北京市", "city": "北京市", "weather": "晴", "temperature": 32.0, "humidity": 45.0 }
}
```

- 链路：取真实 IP → 高德 IP 定位得到 adcode（失败回退默认城市 110000）→ 高德实时天气
- 缓存：IP→adcode 24h，adcode→天气 10min（内存）
- 定位或接口异常 → `code=5001`「天气信息暂时不可用」，前端隐藏天气模块

### 2048 游戏（/api/games/2048）

#### GET /api/games/2048/rank — 排行榜 Top20（公开）

Response：
```json
{
  "code": 200,
  "data": [
    { "rank": 1, "nickname": "钓鱼人", "bestScore": 2048, "maxTile": 2048, "achievedAt": "2026-08-02T10:00:00" }
  ]
}
```

按 `best_score` 降序、`achieved_at` 升序；昵称为空的用户显示「匿名用户」。

#### GET /api/games/2048/my-best — 我的最高分（需登录）

未登录返回 `success(null)`（不报 401）。

#### POST /api/games/2048/score — 提交最高分（需登录）

Request：
```json
{ "bestScore": 2048, "maxTile": 2048 }
```

- 首次提交插入记录，之后仅当更高分时更新（`best_score` 只增不减）
- 未登录 → 401

### 健康检查（/api/health）

#### GET /api/health/readiness — 就绪探针

返回 `ReadinessResponse`（见「例外 2」）。检查项：`app`（进程存活）、`database`（SELECT 1）。
外部服务（热榜/天气）挂了只算功能降级，不影响就绪。

---

## 页面路由

| 路径 | 说明 |
|------|------|
| / | 首页（Header / HeroBanner / 热榜 / 侧栏） |
| /login | 登录页 |
| /register | 注册页 |
| /games | 游戏大厅 |
| /games/2048 | 摸鱼2048 |
| /games/collision | 鱼群碰撞（开发中，未提交） |
| /profile | 个人中心（资料编辑） |

## 登录拦截规则

`LoginInterceptor` 拦截所有 `/api/**`，排除以下公开路径：

```
/api/auth/register
/api/auth/login
/api/users/**              ← Controller 内手动校验 @CurrentUser
/api/hot/**
/api/region/**
/api/weather/**
/api/health/**
/api/daily-sentence/**
/api/games/2048/rank
```

其余 `/api/**`（含 `/api/auth/logout`、`/api/auth/current-user`、`/api/games/2048/score`、`/api/games/2048/my-best`）需登录。
