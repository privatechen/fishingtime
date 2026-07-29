# API 文档

## 统一返回格式

```json
{
  "code": 200,
  "message": "success",
  "data": {},
  "timestamp": 1690700000
}
```

## 错误码

| code | message |
|------|---------|
| 200 | success |
| 400 | 请求参数校验失败 |
| 401 | 未登录 |
| 403 | 无权限 |
| 404 | 资源不存在 |
| 1001 | 用户名已存在 |
| 1002 | 用户不存在 |
| 1003 | 用户名或密码错误 |
| 1004 | 账号已被禁用 |
| 5000 | 系统异常 |

---

## 接口列表

### POST /api/auth/register

注册。

Request：
```json
{
  "username": "fisher",
  "password": "123456",
  "nickname": "钓鱼人",
  "email": "fisher@example.com"
}
```

Response：
```json
{
  "code": 200,
  "data": { "id": 1, "username": "fisher", "nickname": "钓鱼人", "status": 1 }
}
```

### POST /api/auth/login

登录。

Request：
```json
{
  "username": "fisher",
  "password": "123456"
}
```

Response：
```json
{
  "code": 200,
  "data": { "id": 1, "username": "fisher", "nickname": "钓鱼人" }
}
```

### POST /api/auth/logout

退出。需要登录。

Response：
```json
{ "code": 200, "data": null }
```

### GET /api/auth/current-user

获取当前登录用户。需要登录。

Response：
```json
{
  "code": 200,
  "data": { "id": 1, "username": "fisher", "nickname": "钓鱼人" }
}
```

未登录时返回 401。

### GET /api/users/{id}

获取用户信息（公开）。

Response：
```json
{
  "code": 200,
  "data": { "id": 1, "username": "fisher", "nickname": "钓鱼人", "email": null, "avatarUrl": null, "status": 1 }
}
```

## 页面路由

| 路径 | 说明 |
|------|------|
| / | 首页 |
| /login | 登录页 |
| /register | 注册页 |
